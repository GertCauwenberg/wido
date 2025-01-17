/*********************************************************************
  This is developed for the Adafruit nRF52 based Bluefruit LE modules
  Pick one up today in the adafruit shop!
  Adafruit invests time and resources providing this open source code,
  please support Adafruit and open-source hardware by purchasing
  products from Adafruit!
  MIT license, check LICENSE for more information
  All text above, and the splash screen below must be included in
  any redistribution
*********************************************************************/
#include <bluefruit.h>
#include <Adafruit_DotStar.h>
// #include "pitches.h"

// BLE Service
BLEDfu  bledfu;  // OTA DFU service
BLEDis  bledis;  // device information
BLEUart bleuart; // uart over ble
BLEBas  blebas;  // battery
BLEService   bledistance = BLEService("7e48b2f9-ac37-4981-b5ff-fe393cb8eb32"); // distance service
BLECharacteristic bledistanceChar = BLECharacteristic("b2c5a6f5-a2ec-45b4-905d-124eb3500038");
BLECharacteristic blemaxdistanceChar = BLECharacteristic("4db7d558-439e-4547-92e0-8a9deac42f78");

#define REFPOWER       -61    //rssi refference 
#define DISTANCE       2.6    //distance factor
#define HIST_SIZE       5     //number of elements in history

int8_t all_rssi[HIST_SIZE]  = { 0 };
int duration = 500;  // 500 miliseconds
int counter = 0;
int serial;
int maxdistance = 30;
boolean has_connected = false;

#define NUMPIXELS    1         // Number of LEDs in strip
// Here's how to control the LEDs from any two pins:
#define DATAPIN    8
#define CLOCKPIN   6
#define POWERPIN   A2
#define CUTOFFPIN   10
#define SPEAKERPIN 5

Adafruit_DotStar dotstar(NUMPIXELS, DATAPIN, CLOCKPIN, DOTSTAR_BRG);
uint32_t green = 0xFF0000;
uint32_t red = 0x00FF00;
uint32_t blue = 0x0000FF;
uint32_t yellow = dotstar.Color(255, 255, 0);
int batteryLevel = 0;  // last battery level reading from analog input

void startAdv(void)
{
  // Advertising packet
  Bluefruit.Advertising.addFlags(BLE_GAP_ADV_FLAGS_LE_ONLY_GENERAL_DISC_MODE);
  Bluefruit.Advertising.addTxPower();

  // Include bleuart 128-bit uuid
  //Bluefruit.Advertising.addService(bleuart);
  Bluefruit.Advertising.addService(bledistance);


  // Secondary Scan Response packet (optional)
  // Since there is no room for 'Name' in Advertising packet
  Bluefruit.ScanResponse.addName();

  /* Start Advertising
     - Enable auto advertising if disconnected
     - Interval:  fast mode = 20 ms, slow mode = 152.5 ms
     - Timeout for fast mode is 30 seconds
     - Start(timeout) with timeout = 0 will advertise forever (until connected)

     For recommended advertising interval
     https://developer.apple.com/library/content/qa/qa1931/_index.html
  */
  Bluefruit.Advertising.restartOnDisconnect(true);
  Bluefruit.Advertising.setInterval(32, 244);    // in unit of 0.625 ms
  Bluefruit.Advertising.setFastTimeout(30);      // number of seconds in fast mode
  Bluefruit.Advertising.start(0);                // 0 = Don't stop advertising after n seconds
}

float get_distance(const int8_t rssi)
{
  return pow(10, (REFPOWER - rssi) / (10 * DISTANCE));
}

int updateBatteryLevel(int oldBatteryLevel) {
  /* Read the current voltage level on the A0 analog input pin.
     This is used here to simulate the charge level of a battery.
  */
  int raw = analogRead(POWERPIN);
  int newBatteryLevel;

  //Serial.print("Raw read is ");
  //Serial.println(raw);
  
  float mv = raw * 2.0F * 3600.0F/1024.0F;
  if (mv < 3300) { newBatteryLevel = 0; digitalWrite(CUTOFFPIN, LOW);} // Cut-off
  else if (mv < 3600) { newBatteryLevel = (mv-3300)/30; }
  else { newBatteryLevel = 10 + (mv - 3600) * 0.15F; }
  
  if (newBatteryLevel != oldBatteryLevel) {      // if the battery level has changed
    Serial.print("Battery Level % is now: "); // print it
    Serial.println(newBatteryLevel);
    blebas.write(newBatteryLevel);  // and update the battery level characteristic
  }
  return newBatteryLevel;
}


void blink(uint32_t color)
{
  if (dotstar.getPixelColor(0)) {
    dotstar.setPixelColor(0, 0);
  } else {
    dotstar.setPixelColor(0, color);
  }
  dotstar.show();
}

int updateDistance()
{
    uint16_t conn_hdl = Bluefruit.connHandle();

    // Get the reference to current connected connection
    BLEConnection* connection = Bluefruit.Connection(conn_hdl);

    // get the RSSI value of this connection
    // monitorRssi() must be called previously (in connect callback)
    int8_t rssi;
    all_rssi[counter] = connection->getRssi();
    //Serial.printf("Current rssi: %d\n", all_rssi[i]);
    counter += 1;
    if (counter == HIST_SIZE) {
      counter = 0;
    }

    int16_t som;
    for (uint8_t a = 0; a < HIST_SIZE; a++)
    {
      som += all_rssi[a];
    }
    rssi = som / HIST_SIZE;

    //Serial.printf("Rssi = %d\n", rssi);
    return 10*get_distance(rssi);
}

void loop()
{
  batteryLevel = updateBatteryLevel(batteryLevel);
  if ( Bluefruit.connected() )
  {
    uint8_t newDistance = updateDistance();
    
    bledistanceChar.write8((uint8_t) newDistance );

    if ((batteryLevel < 4) || (newDistance > maxdistance)) {
      tone(SPEAKERPIN, 5000, duration);
    }
    
    if (newDistance > maxdistance) {
      blink(red);
    } else if (batteryLevel > 10) {
      blink(green);
    } else {
      blink(yellow);
    }
  }
  else {
  // Unconnected
    if (has_connected) {
      // Failsafe: we were connected, not any more - possibly out of reach
      // -> give signal to return
      tone(SPEAKERPIN, 5000, duration);
    }
  
    if (batteryLevel > 10) {
      blink(blue);
    } else {
      blink(yellow);
    }
  }

  delay(1000);
}

void connect_callback(uint16_t conn_handle)
{
  Serial.println("Connected");

  // Get the reference to current connection
  BLEConnection* connection = Bluefruit.Connection(conn_handle);

  char central_name[32] = { 0 };
  connection->getPeerName(central_name, sizeof(central_name));

  Serial.print("Connected to ");
  Serial.println(central_name);
  has_connected = true;
  
  //Serial.print("RSSI ");
  //Serial.println(connection->getRssi());
  //Serial.print("Address = ");
  //Serial.println(connection->getPeerAddr());

  // Start monitoring rssi of this connection
  // This function should be called in connect callback
  // Input argument is value difference (to current rssi) that triggers callback
  connection->monitorRssi();
}




void rssi_changed_callback(uint16_t conn_hdl, int8_t rssi)
{
  (void) conn_hdl;
  Serial.printf("Callback: Rssi = %d", rssi);
  Serial.println();
}

/**
   Callback invoked when a connection is dropped
   @param conn_handle connection where this event happens
   @param reason is a BLE_HCI_STATUS_CODE which can be found in ble_hci.h
*/
void disconnect_callback(uint16_t conn_handle, uint8_t reason)
{
  (void) conn_handle;
  (void) reason;

  Serial.println();
  Serial.print("Disconnected, reason = 0x"); Serial.println(reason, HEX);
}

void distance_callback(uint16_t conn_hdl, BLECharacteristic* chr, uint8_t* data, uint16_t len)
{
  (void) conn_hdl;
  (void) chr;
  (void) len; // len should be 1

  int new_distance = data[0];
  if (new_distance != maxdistance) {
      Serial.printf("New maximum distance is %d", new_distance);
      maxdistance = new_distance;
  }
  blemaxdistanceChar.write8(maxdistance);
}

void setup()
{
  char name[64];
  Serial.begin(115200);
  //while ( !Serial ) delay(10);   // for nrf52840 with native usb
  delay(2000);

  Serial.println("Bluefruit52 RSSI Example");
  Serial.println("------------------------\n");
  pinMode(SPEAKERPIN, OUTPUT);
  pinMode(POWERPIN, INPUT);
  pinMode(CUTOFFPIN, OUTPUT);
  digitalWrite(CUTOFFPIN, HIGH);
  
  dotstar.begin(); // Initialize pins for output
  dotstar.setBrightness(70);
  dotstar.show();  // Turn all LEDs off ASAP

  // Setup the BLE LED to be enabled on CONNECT
  // Note: This is actually the default behaviour, but provided
  // here in case you want to control this LED manually
  Bluefruit.autoConnLed(false);

  // Config the peripheral connection with maximum bandwidth
  // more SRAM required by SoftDevice
  // Note: All config***() function must be called before begin()
  Bluefruit.configPrphBandwidth(BANDWIDTH_MAX);
  Bluefruit.begin();

  Bluefruit.setTxPower(4);    // Check bluefruit.h for supported values

  Bluefruit.Periph.setConnectCallback(connect_callback);
  Bluefruit.Periph.setDisconnectCallback(disconnect_callback);
  Serial.println("Registered callbacks");
 
  if (!serial) {
    serial = random(1,10000);
  }
  Serial.print("Serial number is ");
  Serial.println(serial);
  int c = Bluefruit.getName(name, 63);
  Serial.print("Name is ");
  Serial.println(name);
  sprintf(name, "WiDo %04d", serial);
  Serial.print("New name: ");
  Serial.println(name);
  Bluefruit.setName(name);

  has_connected = false;
  
  // To be consistent OTA DFU should be added first if it exists
  bledfu.begin();

  // Configure and Start Device Information Service
  bledis.setManufacturer("G&G");
  bledis.setModel("Wireless Dogleash v1");
  bledis.begin();

  // Configure and Start BLE Uart Service
  bleuart.begin();

  // Start BLE Battery Service
  blebas.begin();
  blebas.write(0);

  bledistance.begin();
  bledistanceChar.setProperties(CHR_PROPS_READ | CHR_PROPS_NOTIFY);
  bledistanceChar.setPermission(SECMODE_OPEN, SECMODE_NO_ACCESS);
  bledistanceChar.setFixedLen(1); 
  bledistanceChar.setUserDescriptor("Distance");
  bledistanceChar.begin();
  blemaxdistanceChar.setProperties(CHR_PROPS_READ | CHR_PROPS_WRITE);
  blemaxdistanceChar.setPermission(SECMODE_OPEN, SECMODE_OPEN);
  blemaxdistanceChar.setFixedLen(1); 
  blemaxdistanceChar.setWriteCallback(distance_callback);
  blemaxdistanceChar.setUserDescriptor("Max Distance");
  blemaxdistanceChar.begin();
  blemaxdistanceChar.write8(maxdistance);
  
  // Set up Rssi changed callback
  Bluefruit.setRssiCallback(rssi_changed_callback);

  // Set up and start advertising
  startAdv();

  Serial.println("Please use Adafruit's Bluefruit LE app to connect");
}
