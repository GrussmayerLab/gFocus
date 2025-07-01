#include "gFocus.h"
#include "protocol.h"
#include "ModuleInterface.h"

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

const char* g_CameraDeviceName = "gFocus Light Sensor";
const char* g_versionProp = "Version";

const char* g_PixelType_16bit = "16bit";


MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_CameraDeviceName, MM::CameraDevice, "gFocus Light Sensor");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == 0)
		return 0;

	// decide which device class to create based on the deviceName parameter
	if (strcmp(deviceName, g_CameraDeviceName) == 0)
	{
		// create camera
		return new gFocus();
	}

	return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// CDECamera implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
* CDECamera constructor.
* Setup default all variables and create device properties required to exist
* before intialization. In this case, no such properties were required. All
* properties will be created in the Initialize() method.
*
* As a general guideline Micro-Manager devices do not access hardware in the
* the constructor. We should do as little as possible in the constructor and
* perform most of the initialization in the Initialize() method.
*/

gFocus::gFocus() :
	CCameraBase<gFocus>(),
	initialized_(false)
{
	// call the base class method to set-up default error codes/messages
	InitializeDefaultErrorMessages();

	CPropertyAction* pAct = new CPropertyAction(this, &gFocus::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
	settings_.avarage = 1;
	settings_.exposure = 1.0;
	img_ = ImgBuffer(imageWidth_, imageHeight_, 2);
}

/**
* gFocus destructor.
* If this device used as intended within the Micro-Manager system,
* Shutdown() will be always called before the destructor. But in any case
* we need to make sure that all resources are properly released even if
* Shutdown() was not called.
*/
gFocus::~gFocus()
{
}

/**
* Obtains device name.
* Required by the MM::Device API.
*/
void gFocus::GetName(char* name) const
{
	// We just return the name we use for referring to this
	// device adapter.
	CDeviceUtils::CopyLimitedString(name, g_CameraDeviceName);
}

bool gFocus::ReadExactBytes(const std::string& port, uint8_t* buffer, size_t expectedBytes, unsigned long timeoutMs)
{
	unsigned long totalRead = 0;
	unsigned long startTime = GetClockTicksUs() / 1000; // Convert µs to ms

	while (totalRead < expectedBytes) {
		unsigned long bytesRead = 0;
		int ret = ReadFromComPort(port.c_str(), buffer + totalRead, static_cast<unsigned>(expectedBytes - totalRead), bytesRead);
		if (ret != DEVICE_OK) return false;

		totalRead += bytesRead;

		unsigned long now = GetClockTicksUs() / 1000;
		if (now - startTime > timeoutMs) break;

		CDeviceUtils::SleepMs(1); // Small delay to avoid busy looping
	}

	return totalRead == expectedBytes;
}


/**
* Performs exposure and grabs a single image.
* This function should block during the actual exposure and return immediately afterwards
* (i.e., before readout).  This behavior is needed for proper synchronization with the shutter.
* Required by the MM::Camera API.
*/
int gFocus::SnapImage()
{
	LogMessage("SnapImage...", false);
	std::vector<uint8_t> lowerAll(128);
	std::vector<uint8_t> upperAll(128);
	std::vector<uint8_t> combined(256);

	const int MAX_RETRIES = 3;

	// Send settings
	std::vector<uint8_t> message = protocol_.createSetSettingsMessage(settings_);
	int ret = WriteToComPort(port_.c_str(), message.data(), static_cast<unsigned int>(message.size()));
	if (ret != DEVICE_OK) return ret;

	// Trigger acquisition
	message = protocol_.createGetDataMessage();
	ret = WriteToComPort(port_.c_str(), message.data(), static_cast<unsigned int>(message.size()));
	if (ret != DEVICE_OK) return ret;

	std::vector<uint8_t> chunkLower(37);
	std::vector<uint8_t> chunkUpper(37);
	unsigned long bytesRead = 0;

	for (int chunk = 0; chunk < 4; ++chunk) {
		bool success = false;
		for (int retry = 0; retry < MAX_RETRIES; ++retry) {
			// --- Read Lower Bytes ---
			if (!ReadExactBytes(port_, chunkLower.data(), 37, 200)) {  // 200 ms timeout
				LogMessage("Timeout waiting for chunk " + std::to_string(chunk) + " lower bytes.");
				return DEVICE_ERR;
			}

			if (!protocol_.validateDataMessage(chunkLower)) {
				std::ostringstream oss;
				for (uint8_t byte : chunkLower) {
					oss << std::hex << std::setw(2) << std::setfill('0') << static_cast<int>(byte) << " ";
				}

				LogMessage("Chunk " + std::to_string(chunk) + " lower invalid, retry " + std::to_string(retry) + " data: " + oss.str());				
				uint8_t nack = 0x55;
				WriteToComPort(port_.c_str(), &nack, 1);
				continue;
			}

			// --- Read Upper Bytes ---
			if (!ReadExactBytes(port_, chunkUpper.data(), 37, 200)) {  // 200 ms timeout
				LogMessage("Timeout waiting for chunk " + std::to_string(chunk) + " lower bytes.");
				return DEVICE_ERR;
			}

			if (!protocol_.validateDataMessage(chunkUpper)) {
				LogMessage("Chunk " + std::to_string(chunk) + " upper invalid, retry " + std::to_string(retry));
				uint8_t nack = 0x55;
				WriteToComPort(port_.c_str(), &nack, 1);
				continue;
			}

			// Both passed validation
			uint8_t ack = 0xAA;
			WriteToComPort(port_.c_str(), &ack, 1);
			std::copy(chunkLower.begin() + 3, chunkLower.end() - 2, lowerAll.begin() + chunk * 32);
			std::copy(chunkUpper.begin() + 3, chunkUpper.end() - 2, upperAll.begin() + chunk * 32);
			success = true;
			break;
		}

		if (!success) {
			LogMessage("Failed to get valid chunk " + std::to_string(chunk) + " after retries.");
			return DEVICE_ERR;
		}
	}

	// Reconstruct final combined image buffer
	for (size_t i = 0; i < 128; ++i) {
		combined[i * 2] = lowerAll[i];       // Low byte
		combined[i * 2 + 1] = upperAll[i];   // High byte
	}

	uint8_t* pixels = img_.GetPixelsRW();
	std::copy(combined.begin(), combined.end(), pixels);

	LogMessage("SnapImage done.");
	return DEVICE_OK;
}


/**
* Returns pixel data.
* Required by the MM::Camera API.
* The calling program will assume the size of the buffer based on the values
* obtained from GetImageBufferSize(), which in turn should be consistent with
* values returned by GetImageWidth(), GetImageHight() and GetImageBytesPerPixel().
* The calling program allso assumes that camera never changes the size of
* the pixel buffer on its own. In other words, the buffer can change only if
* appropriate properties are set (such as binning, pixel type, etc.)
*/
const unsigned char* gFocus::GetImageBuffer()
{
	return img_.GetPixels();
}

/**
* Returns image buffer X-size in pixels.
* Required by the MM::Camera API.
*/
unsigned gFocus::GetImageWidth() const
{
	return img_.Width();
}

/**
* Returns image buffer Y-size in pixels.
* Required by the MM::Camera API.
*/
unsigned gFocus::GetImageHeight() const
{
	return img_.Height();
}

/**
* Returns image buffer pixel depth in bytes.
* Required by the MM::Camera API.
*/
unsigned gFocus::GetImageBytesPerPixel() const
{
	return img_.Depth();
}

/**
* Returns the bit depth (dynamic range) of the pixel.
* This does not affect the buffer size, it just gives the client application
* a guideline on how to interpret pixel values.
* Required by the MM::Camera API.
*/
unsigned gFocus::GetBitDepth() const
{
	return bitDepth_;
}

/**
* Returns the size in bytes of the image buffer.
* Required by the MM::Camera API.
*/
long gFocus::GetImageBufferSize() const
{
	return img_.Width() * img_.Height() * GetImageBytesPerPixel();
}

/**
* Returns the current exposure setting in milliseconds.
* Required by the MM::Camera API.
*/
double gFocus::GetExposure() const
{
	return settings_.exposure;
}

/**
* Returns the current exposure setting in milliseconds.
* Required by the MM::Camera API.
*/
void gFocus::SetExposure(double exp_ms)
{
	settings_.exposure = exp_ms;
}

int gFocus::GetAveraging() const
{
	return settings_.avarage;
}

void gFocus::SetAveraging(int avarage)
{
	settings_.avarage = avarage;
}

// Not used
int gFocus::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
	return DEVICE_OK;
}

// Not used
int gFocus::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
	return DEVICE_OK;
}

// Not used 
double gFocus::GetNominalPixelSizeUm() const
{
	return 0.0;
}

// Not used
double gFocus::GetPixelSizeUm() const
{
	return 0.0;
}

/**
* Returns the current binning factor.
* Required by the MM::Camera API.
*/
int gFocus::GetBinning() const
{
	char buf[MM::MaxStrLength];
	int ret = GetProperty(MM::g_Keyword_Binning, buf);
	if (ret != DEVICE_OK)
		return 1;
	return atoi(buf);
}

/**
* Sets binning factor.
* Required by the MM::Camera API.
*/
int gFocus::SetBinning(int binFactor)
{
	return SetProperty(MM::g_Keyword_Binning, CDeviceUtils::ConvertToString(binFactor));
}

// Not used
int gFocus::ClearROI()
{
	return 0;
}

int gFocus::IsExposureSequenceable(bool& isSequenceable) const
{
	return 0;
}

/**
* Intializes the hardware.
* Required by the MM::Device API.
* Typically we access and initialize hardware at this point.
* Device properties are typically created here as well, except
* the ones we need to use for defining initialization parameters.
* Such pre-initialization properties are created in the constructor.
* (This device does not have any pre-initialization properties)
*/
int gFocus::Initialize()
{
	// Name
	int ret = CreateProperty(MM::g_Keyword_Name, g_CameraDeviceName, MM::String, true);
	if (DEVICE_OK != ret)
		return ret;

	// The first second or so after opening the serial port, the Arduino is waiting for firmwareupgrades.  Simply sleep 1 second.
	CDeviceUtils::SleepMs(1000);

	//const std::lock_guard<std::mutex> lock(mutex_);

	// Check that we have a controller:
	PurgeComPort(port_.c_str());
	ret = GetControllerVersion(version_);
	if (DEVICE_OK != ret) {
		std::cout << "get version error" << std::endl;
		return ret;
	}

	// binning
	CPropertyAction* pAct = new CPropertyAction(this, &gFocus::OnBinning);
	int nRet = CreateIntegerProperty(MM::g_Keyword_Binning, 1, false, pAct);

	CPropertyAction* pActA = new CPropertyAction(this, &gFocus::OnExposure);
	nRet = CreateProperty("Time [ms]", "1.0", MM::Float, false, pActA);

	CPropertyAction* pActB = new CPropertyAction(this, &gFocus::OnAverage);
	nRet = CreateProperty("Average #", "1", MM::Integer, false, pActB);

	pAct = new CPropertyAction(this, &gFocus::OnPixelType);
	nRet = CreateStringProperty(MM::g_Keyword_PixelType, g_PixelType_16bit, false, pAct);
	assert(nRet == DEVICE_OK);

	std::vector<std::string> pixelTypeValues;
	pixelTypeValues.push_back(g_PixelType_16bit);

	nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
	if (nRet != DEVICE_OK)
		return nRet;

	// Bit depth
	pAct = new CPropertyAction(this, &gFocus::OnBitDepth);
	nRet = CreateIntegerProperty("BitDepth", 12, false, pAct);
	assert(nRet == DEVICE_OK);

	std::vector<std::string> bitDepths;
	bitDepths.push_back("12");
	nRet = SetAllowedValues("BitDepth", bitDepths);
	if (nRet != DEVICE_OK)
		return nRet;

	nRet = UpdateStatus();
	if (nRet != DEVICE_OK)
		return nRet;

	unsigned char* pBuf = const_cast<unsigned char*>(img_.GetPixels());
	memset(pBuf, 0, img_.Height() * img_.Width() * img_.Depth());

	initialized_ = true;

	return DEVICE_OK;
}
///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int gFocus::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		// nothing to do, let the caller use cached property
	}
	else if (eAct == MM::AfterSet)
	{

		double exposure;
		pProp->Get(exposure);
		SetExposure(exposure);
		return DEVICE_OK;
	}

	return DEVICE_OK;
}

int gFocus::OnAverage(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{

	}
	else if (eAct == MM::AfterSet)
	{
		char buf[100];
		long average;
		pProp->Get(average);
		SetAveraging((int)average);
		sprintf(buf, "driver average: %d", average);
		LogMessage(buf);
		return DEVICE_OK;
	}
	return DEVICE_OK;
}

int gFocus::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;

	switch (eAct)
	{
	case MM::AfterSet:
		if (IsCapturing())
			return DEVICE_CAMERA_BUSY_ACQUIRING;

		long bin;
		pProp->Get(bin);
		binSize_ = static_cast<int>(bin);
		break;

	case MM::BeforeGet:
		pProp->Set(static_cast<long>(binSize_));
		break;

	default:
		break;
	}

	return ret;
}

int gFocus::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_ERR;
	switch (eAct)
	{
	case MM::AfterSet:
	{
		if (IsCapturing())
			return DEVICE_CAMERA_BUSY_ACQUIRING;

		std::string pixelType;
		pProp->Get(pixelType);

		if (pixelType.compare(g_PixelType_16bit) == 0)
		{
			img_.Resize(img_.Width(), img_.Height(), 2);
			bitDepth_ = 16;
			ret = DEVICE_OK;
		}
	}
	break;
	case MM::BeforeGet:
	{
		long bytesPerPixel = GetImageBytesPerPixel();

		if (bytesPerPixel == 2)
		{
			pProp->Set(g_PixelType_16bit);
		}

		ret = DEVICE_OK;
	} break;
	default:
		break;
	}
	return ret;
}

int gFocus::OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_ERR;
	switch (eAct)
	{
	case MM::AfterSet:
	{
		if (IsCapturing())
			return DEVICE_CAMERA_BUSY_ACQUIRING;

		long bitDepth;
		pProp->Get(bitDepth);

		unsigned int bytesPerComponent;

		switch (bitDepth) {
		case 8:
			bytesPerComponent = 1;
			bitDepth_ = 8;
			ret = DEVICE_OK;
			break;
		case 10:
			bytesPerComponent = 2;
			bitDepth_ = 10;
			ret = DEVICE_OK;
			break;
		case 11:
			bytesPerComponent = 2;
			bitDepth_ = 11;
			ret = DEVICE_OK;
			break;
		case 12:
			bytesPerComponent = 2;
			bitDepth_ = 12;
			ret = DEVICE_OK;
			break;
		case 14:
			bytesPerComponent = 2;
			bitDepth_ = 14;
			ret = DEVICE_OK;
			break;
		case 16:
			bytesPerComponent = 2;
			bitDepth_ = 16;
			ret = DEVICE_OK;
			break;
		case 32:
			bytesPerComponent = 4;
			bitDepth_ = 32;
			ret = DEVICE_OK;
			break;
		default:
			// on error switch to default pixel type
			bytesPerComponent = 1;

			pProp->Set((long)8);
			bitDepth_ = 8;
			ret = 102;
			break;
		}
		char buf[MM::MaxStrLength];
		GetProperty(MM::g_Keyword_PixelType, buf);
		std::string pixelType(buf);
		unsigned int bytesPerPixel = 1;


		// automagickally change pixel type when bit depth exceeds possible value

		if (pixelType.compare(g_PixelType_16bit) == 0)
		{
			bytesPerPixel = 2;
		}

		img_.Resize(img_.Width(), img_.Height(), bytesPerPixel);

	} break;
	case MM::BeforeGet:
	{
		pProp->Set((long)bitDepth_);
		ret = DEVICE_OK;
	} break;
	default:
		break;
	}
	return ret;
}




int gFocus::OnPort(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
	{
		pProp->Set(port_.c_str());
	}
	else if (pAct == MM::AfterSet)
	{
		pProp->Get(port_);
		portAvailable_ = true;
	}
	return DEVICE_OK;
}

int gFocus::GetControllerVersion(int& version)
{
	std::vector<uint8_t> message = protocol_.createGetDeviceMessage();

	int ret = WriteToComPort(port_.c_str(), message.data(), static_cast<unsigned int>(message.size()));
	if (ret != DEVICE_OK) return ret;

	std::string ans;

	ret = GetSerialAnswer(port_.c_str(), "\x7F", ans);
	ans = ans + "\x7F";
	std::vector<uint8_t> buffer(ans.begin(), ans.end());
	std::ostringstream oss;
	for (uint8_t b : buffer) {
		oss << std::hex << std::setw(2) << std::setfill('0') << static_cast<int>(b) << " ";
	}
	LogMessage("Message: " + oss.str(), true);

	bool success = protocol_.validateDeviceMessage(buffer);

	version = 1;

	if (success)
		return DEVICE_OK;
	else
		LogMessage(protocol_.getError());
	return DEVICE_NOT_CONNECTED;
}

int gFocus::OnVersion(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
	{
		pProp->Set((long)version_);
	}
	return DEVICE_OK;
}

/**
* Shuts down (unloads) the device.
* Required by the MM::Device API.
* Ideally this method will completely unload the device and release all resources.
* Shutdown() may be called multiple times in a row.
* After Shutdown() we should be allowed to call Initialize() again to load the device
* without causing problems.
*/
int gFocus::Shutdown()
{
	initialized_ = false;
	return DEVICE_OK;
}


