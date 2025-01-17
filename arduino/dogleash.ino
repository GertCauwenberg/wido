#include <ArduinoBLE.h>

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLEAdvertisedDevice.h>

// Comment this line out for the final version (terse output in the serial monitor)
#define VERBOSE

#define POT_PIN        0 // Analog pin 0
#define BATERY_PIN     1 // Analog pin 1
#define BUZZER_PIN     4
#define TRIGGER_PIN    7
#define ECHO_PIN       8
#define LED_PIN       12
#define MAX_DISTANCE 300
#define BUZZER_SECS    3

class Monitor: public BLEServerCallbacks
{
  public:
    static int16_t connection_id;

    // Motor state bits
    enum motor_states { ENABLED, ON };
    static bool motor_state;

    // Durations for motor ON and motor OFF in milliseconds
    // Note: make sure OFF_DELAY + ON_DELAY is equal to 1000!
    static constexpr uint32_t ON_DELAY = 300;
    static constexpr uint32_t OFF_DELAY = 700;

    /* dBm to distance parameters; How to update distance_factor 1.place the
       phone at a known distance (2m, 3m, 5m, 10m) 2.average about 10 RSSI
       values for each of these distances, Set distance_factor so that the
       calculated distance approaches the actual distances, e.g. at 5m. */
    static constexpr float reference_power  = -50; //rssi reffrence
    static constexpr float distance_factor = 3.5;

    static constexpr int8_t motor_threshold = -65;

    uint8_t get_value() {
      return value++;
    }
    esp_err_t get_rssi() {
      return esp_ble_gap_read_rssi(remote_addr);
    }

    static float get_distance(const int8_t rssi)
    {
      return pow(10, (reference_power - rssi) / (10 * distance_factor));
    }

  private:
    void onConnect(BLEServer* pServer, esp_ble_gatts_cb_param_t *param)
    {
      // Update connection variables
      connection_id = param->connect.conn_id;
      memcpy(&remote_addr, param->connect.remote_bda, sizeof(remote_addr));

      // Install the RSSI callback
      BLEDevice::setCustomGapHandler(&Monitor::rssi_event);

#ifdef VERBOSE
      // Show new connection info
      Serial.printf("Connection #: %i, remote: ", connection_id);
      show_address(param->connect.remote_bda);
      Serial.printf(" [Callback installed]\n");
#endif

#ifdef CONNECT_SIGNALLING
      digitalWrite(LED_CONNECT_PIN, HIGH);
#endif
    }

    void onDisconnect(BLEServer* pServer)
    {
      Serial.printf("Connection #%i closed\n", connection_id);
      BLEDevice::setCustomGapHandler(nullptr);
      connection_id = -1;

#ifdef CONNECT_SIGNALLING
      digitalWrite(LED_CONNECT_PIN, LOW);
#endif
    }

    static void rssi_event(esp_gap_ble_cb_event_t event, esp_ble_gap_cb_param_t *param);

    static esp_bd_addr_t remote_addr;
    uint8_t value = 0;
};



void setup() {
  pinMode(POT_PIN, OUTPUT);
  pinMode(BATERY_PIN, OUTPUT);
  pinMode(LED_PIN, OUTPUT);
}

void loop() {
  int volts = map(analogRead(BATERY_PIN), 0, 1023, 0, 500);
  if (volts > 300 && volts < 370) batteryDead();
}

void buzzer() {
  for (int i = 0; i < BUZZER_SECS * 10; i++) {
    digitalWrite(LED_PIN, HIGH);
    tone(BUZZER_PIN, 4000);
    delay(50);
    digitalWrite(LED_PIN, LOW);
    tone(BUZZER_PIN, 3800);
    delay(50);
  }
  noTone(BUZZER_PIN);
}

void batteryDead() {
  while (1) {
    digitalWrite(LED_PIN, HIGH);
    tone(BUZZER_PIN, 500);
    delay(200);
    digitalWrite(LED_PIN, LOW);
    noTone(BUZZER_PIN);
    delay(1000);
  }
}
