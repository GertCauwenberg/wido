/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.wido;

import android.os.ParcelUuid;

import java.util.UUID;

/**
 * Constants for use in the Bluetooth Advertisements sample
 */
public class Constants {

    /**
     * UUID identified with this app - set as Service UUID for BLE Advertisements.
     *
     * Bluetooth requires a certain format for UUIDs associated with Services.
     * The official specification can be found here:
     * {@link https://www.bluetooth.org/en-us/specification/assigned-numbers/service-discovery}
     */
    public static final ParcelUuid Distance_Service_UUID = ParcelUuid
            .fromString("7e48b2f9-ac37-4981-b5ff-fe393cb8eb32");
    public final static ParcelUuid Battery_Service_UUID = ParcelUuid.fromString("0000180F-0000-1000-8000-00805F9B34FB");
    public final static ParcelUuid Battery_Characteristic_UUID = ParcelUuid.fromString("00002A19-0000-1000-8000-00805F9B34FB");
    public final static ParcelUuid Distance_Characteristic_UUID = ParcelUuid.fromString("b2c5a6f5-a2ec-45b4-905d-124eb3500038");
    public final static ParcelUuid MaxDistance_Characteristic_UUID = ParcelUuid.fromString("4db7d558-439e-4547-92e0-8a9deac42f78");

    public static final int REQUEST_ENABLE_BT = 1;

}
