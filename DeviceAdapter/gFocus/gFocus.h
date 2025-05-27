///////////////////////////////////////////////////////////////////////////////
// FILE:          gFocus.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   A light sensor
//
// AUTHOR:        Jelle Komen
//
// COPYRIGHT:     2025 Jelle Komen
// LICENSE:       Licensed under the Apache License, Version 2.0 (the "License");
//                you may not use this file except in compliance with the License.
//                You may obtain a copy of the License at
//                
//                http://www.apache.org/licenses/LICENSE-2.0
//                
//                Unless required by applicable law or agreed to in writing, software
//                distributed under the License is distributed on an "AS IS" BASIS,
//                WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//                See the License for the specific language governing permissions and
//                limitations under the License.

#pragma once

#include <string>
#include "protocol.h"
#include "DeviceBase.h"
#include "ImgBuffer.h"

extern const char* cameraName;


class gFocus : public CCameraBase<gFocus>
{
public:
	gFocus();
	~gFocus();

	// Inherited via CCameraBase
	int Initialize();
	// property handlers
	int OnPort(MM::PropertyBase* pPropt, MM::ActionType eAct);
	int OnVersion(MM::PropertyBase* pPropt, MM::ActionType eAct);
	int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAverage(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct);

	int Shutdown();
	void GetName(char* name) const;
	int SnapImage();
	const unsigned char* GetImageBuffer();
	double GetExposure() const;
	void SetExposure(double exp_ms);
	int  GetAveraging() const;
	void SetAveraging(int avarage);
	int SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize);
	int GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize);
	long GetImageBufferSize() const;
	virtual double GetNominalPixelSizeUm() const;
	virtual double GetPixelSizeUm() const;
	int GetBinning() const;
	int SetBinning(int binSize);
	int ClearROI();
	virtual int IsExposureSequenceable(bool& isSequenceable) const;
	unsigned GetImageWidth() const;
	unsigned GetImageHeight() const;
	unsigned GetBitDepth() const;
	unsigned GetImageBytesPerPixel() const;

	int WriteToComPortH(const unsigned char* command, unsigned len) { return WriteToComPort(port_.c_str(), command, len); }
	int ReadFromComPortH(unsigned char* answer, unsigned maxLen, unsigned long& bytesRead)
	{
		return ReadFromComPort(port_.c_str(), answer, maxLen, bytesRead);
	}
private:
	int GetControllerVersion(int& version);
	bool initialized_;
	Protocol::SetSettingsCommand settings_;
	ImgBuffer img_;
	int binSize_ = 2;
	int bitDepth_ = 12;
	unsigned imageWidth_ = 128;
	unsigned imageHeight_ = 1;
	int version_;
	Protocol protocol_;
	std::string port_;
	bool portAvailable_;
};
