#include <SPI.h>
#include <Arduino.h>

#define CMD_GET_DEVICE 0x01
#define CMD_SET_SETTINGS 0x2
#define CMD_GET_DATA 0x03

#define START_FLAG 0x7E
#define END_FLAG 0x7F
#define DEVICE_ID 0x01
#define RESPONSE_ERROR 0xFF
#define BUFFER_SIZE 64
#define QUEUE_SIZE 10 // Number of messages in FIFO

#define S_CLK 9
#define S_DIN 8
#define S_OUT A5

#define TIMER_INTERVAL_FQ 10  // 10 Hz

#define ACK 0xAA
#define NACK 0x55
#define MAX_RETRIES 3
#define ACK_TIMEOUT_MS 100

float exposureTime = 0.1f;  // 100 µs
uint16_t averagedData[128] = {0};
uint16_t tempBuffer[128];
uint8_t averageCount = 1;     // Average 1 run

// FIFO Queue for messages
typedef struct {
    uint8_t data[BUFFER_SIZE];
    int length;
} Message;

Message fifo_queue[QUEUE_SIZE];
int fifo_head = 0;
int fifo_tail = 0;

uint8_t Serial_buffer[BUFFER_SIZE];
int buffer_index = 0;

void checkAndExtractMessages();
void processSerialCommand();
uint8_t calculateCRC(uint8_t *args, int argCount);
void handleGetDevice();
void handleGetSettings(uint8_t average, float exposure);
void initializeLightSensor();
void onTimerInterrupt();
void runMeasurement(uint16_t* buffer);
void sendData(uint16_t* buffer, size_t length);
bool waitForAck(unsigned long timeout = ACK_TIMEOUT_MS);
// FIFO Helper Functions
bool isFIFOFull() { return ((fifo_head + 1) % QUEUE_SIZE) == fifo_tail; }
bool isFIFOEmpty() { return fifo_head == fifo_tail; }

void ClockPulse() {
  delayMicroseconds(1);
  digitalWrite(S_CLK, HIGH);
  delayMicroseconds(1);
  digitalWrite(S_CLK, LOW);
}

void enqueueMessage(uint8_t *data, int length) {
    if (!isFIFOFull()) {
        memcpy(fifo_queue[fifo_head].data, data, length);
        fifo_queue[fifo_head].length = length;
        fifo_head = (fifo_head + 1) % QUEUE_SIZE;
    }
}

bool dequeueMessage(Message *msg) {
    if (!isFIFOEmpty()) {
        *msg = fifo_queue[fifo_tail];
        fifo_tail = (fifo_tail + 1) % QUEUE_SIZE;
        return true;
    }
    return false;
}

void setup() {
    digitalWrite(LED_BUILTIN, LOW);
    Serial.begin(115200);
    pinMode(S_CLK, OUTPUT);
    pinMode(S_DIN, OUTPUT);
    // analogReadResolution(12); // Set to 12 bits
    initializeLightSensor();
}

void loop() {
  // triggerCamera();
  // delay(1000);
    int ch = Serial.read();
    if (ch != -1) {
        Serial_buffer[buffer_index++] = (uint8_t)ch;

        if (buffer_index >= BUFFER_SIZE) {
            buffer_index = 0; // Prevent overflow
        }

        // Extract messages from buffer and store them in FIFO
        checkAndExtractMessages();
    }

    // Process the next available command in FIFO
    if (!isFIFOEmpty()) {
        processSerialCommand();
    }
}

void checkAndExtractMessages() {
    int startIdx = -1;
    int endIdx = -1;

    // Scan the buffer to find the first START_FLAG and END_FLAG
    for (int i = 0; i < buffer_index; i++) {
        if (Serial_buffer[i] == START_FLAG && startIdx == -1) {
            startIdx = i; // Mark the start of a valid message
        }
        if (Serial_buffer[i] == END_FLAG && startIdx != -1) {
            endIdx = i; // Found a complete message
            int messageLength = endIdx - startIdx + 1;

            // Store the valid message in FIFO
            enqueueMessage(&Serial_buffer[startIdx], messageLength);

            // Remove processed message from buffer
            memmove(Serial_buffer, &Serial_buffer[endIdx + 1], BUFFER_SIZE - (endIdx + 1));
            buffer_index -= (endIdx + 1);

            // Reset start/end markers and continue scanning
            startIdx = -1;
            endIdx = -1;
            i = -1; // Restart loop from beginning
        }
    }

    // If buffer is full but no complete message, reset to prevent overflow
    if (buffer_index >= BUFFER_SIZE) {
        buffer_index = 0;
        memset(Serial_buffer, 0, sizeof(Serial_buffer));
    }
}

void processSerialCommand() {
    Message msg;
    if (!dequeueMessage(&msg)) return;

    uint8_t command, length, receivedCRC, calculatedCRC;
    uint8_t channel_id;
    uint8_t average;
    float exposure;
    uint16_t value;

    if (msg.length < 4) return; // Ignore incomplete packets

    command = msg.data[1];
    length = msg.data[2];
    receivedCRC = msg.data[length];

    // Validate CRC
    calculatedCRC = calculateCRC(&msg.data[1], length - 1);
    if (calculatedCRC != receivedCRC) {
        Serial.write(RESPONSE_ERROR);
        return;
    }

    switch (command) {
        case CMD_GET_DEVICE:
            digitalWrite(LED_BUILTIN, HIGH);  // turn the LED on (HIGH is the voltage level)
            handleGetDevice();
            break;
        case CMD_SET_SETTINGS:
            if (length < 6) {
                Serial.write(RESPONSE_ERROR);
                return;
            }
            average = msg.data[3]; // uint8_t
            memcpy(&exposure, &msg.data[4], sizeof(float)); // Extract float from bytes
            exposureTime = exposure;
            averageCount = average;
            break;
        case CMD_GET_DATA:
            triggerCamera();
            break;
        default:
            Serial.write(RESPONSE_ERROR);
            break;
    }
}

uint8_t calculateCRC(uint8_t *args, int argCount) {
    uint8_t crc = 0;
    for (int i = 0; i < argCount; i++) {
        crc ^= args[i];
    }
    return crc;
}

void handleGetDevice() {
    uint8_t response[] = {CMD_GET_DEVICE, 0x01, DEVICE_ID};
    uint8_t crc = calculateCRC(response, 3);

    Serial.write(START_FLAG);
    for (int i = 0; i < 3; i++) {
        Serial.write(response[i]);
    }
    Serial.write(crc);
    Serial.write(END_FLAG);
}

void triggerCamera() {
  // Clear sum buffer if it's the first average cycle
  memset(averagedData, 0, sizeof(averagedData));

  for (int n = 0; n < averageCount; n++) {
    runMeasurement(tempBuffer);

    // Sum up results
    for (int i = 0; i < 128; i++) {
      averagedData[i] += tempBuffer[i];
    }
  }

  // Final average
  for (int i = 0; i < 128; i++) {
    averagedData[i] /= averageCount;
  }

  // Send data in four chunks of 32 values each
  for (int chunk = 0; chunk < 4; chunk++) {
    int start = chunk * 32;
    int retries = 0;
    bool success = false;

    while (retries < MAX_RETRIES) {
      sendData(&averagedData[start], 32);
      if (waitForAck()) {
        success = true;
        break;
      } else {
        retries++;
      }
    }
  }
}

void runMeasurement(uint16_t* buffer) {
  digitalWrite(S_DIN, HIGH);
  ClockPulse();
  digitalWrite(S_DIN, LOW);

  unsigned int microSeconds = (unsigned int) exposureTime * 1000;
  delayMicroseconds(microSeconds);

  buffer[0] = analogRead(S_OUT);

  for (int i = 1; i < 128; i++) {
    ClockPulse();
    buffer[i] = analogRead(S_OUT);
  }
  ClockPulse();
  delayMicroseconds(20);
}

void sendData(uint16_t* buffer, size_t length) {
  // Total size: CMD (1) + LEN (1) + DATA (length) + CRC (1) + flags (2)
  size_t payloadSize = 1 + 1 + 1*length; 
  uint8_t response[payloadSize+3];

  // First packet: low bytes
  response[0] = CMD_GET_DATA;
  response[1] = length;

  for (size_t i = 0; i < length; i++) {
    response[2 + i] = buffer[i] & 0xFF;  // Low byte
  }


  uint8_t crc1 = calculateCRC(response, payloadSize);

  Serial.write(START_FLAG);
  Serial.write(response, payloadSize);
  Serial.write(crc1);
  Serial.write(END_FLAG);

  delayMicroseconds(1000);

  // Second packet: high bytes
  response[0] = CMD_GET_DATA;  // different command for clarity
  response[1] = length;
  
  for (size_t i = 0; i < length; i++) {
    response[2 + i] = (buffer[i] >> 8) & 0xFF;  // High byte
  }

  uint8_t crc2 = calculateCRC(response, payloadSize);

  Serial.write(START_FLAG);
  Serial.write(response, payloadSize);
  Serial.write(crc2);
  Serial.write(END_FLAG);
}

void initializeLightSensor() {
  // Clock out any excisting SI pulse through the CCD register
  for (int i = 0; i < 130; i++) {
    ClockPulse();
  }

  // Send an SI pulse and clock out the sensor register
  digitalWrite(S_DIN, HIGH);
  ClockPulse();
  digitalWrite(S_DIN, LOW);

  for (int i = 1; i < 128; i++) {
    ClockPulse();
  }
}

bool waitForAck(unsigned long timeout = ACK_TIMEOUT_MS) {
  unsigned long startTime = millis();
  while (millis() - startTime < timeout) {
    if (Serial.available()) {
      int resp = Serial.read();
      if (resp == ACK) return true;
      if (resp == NACK) return false;
    }
  }
  return false;  // Timeout
}

