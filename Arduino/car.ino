#include <Servo.h>

#include <WiFi.h>

#include "Arduino.h"

#include <ESPmDNS.h>

#include <ArduinoJson.h>
#include <LiquidCrystal.h>

// rs, enable, d4, d5, d6, d7
LiquidCrystal lcd(27, 26, 25, 33, 32, 17);

Servo throttleMotor, steeringMotor;

static const int throttlePin = 18; // 26
static const int steeringPin = 19; // 25

const char* ssid = "AutoDen";
const char* password = "autoden17";
const char * hostName = "esp-autoden";

void setup() {
  Serial.begin(115200);
  while (!Serial) {
    // wait serial port initialization
  }
  Serial.setDebugOutput(true);

  throttleMotor.attach(throttlePin);
  steeringMotor.attach(steeringPin);

  // set up the LCD's number of rows and columns:
  lcd.begin(16, 2);
  // Print a message to the LCD.

  delay(10);

  Serial.println();
  Serial.println();
  Serial.print("Connecting to ");
  Serial.println(ssid);

  lcd.print("Wifi connecting");

  //WiFi.setHostname(hostName);
  //WiFi.mode(WIFI_AP_STA);
  //WiFi.softAP(hostName);
  WiFi.begin(ssid, password);

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  lcd.clear();
  lcd.setCursor(0, 1);

  setSteering(0);
  setSpeed(0);

  Serial.println("");
  Serial.println("WiFi connected");
  Serial.println("IP address: ");
  Serial.println(WiFi.localIP());

  MDNS.addService("http", "tcp", 80);
}

void writeLine(int row, String text) {
  Serial.println(text);
  lcd.setCursor(0, row);
  lcd.print("                ");
  lcd.setCursor(0, row);
  lcd.print(text);
}

void setSteering(int angle) {
  int degrees = (angle + 100) / ((double) 200 / 90);
  writeLine(0, String("Steer: ") + angle + String("% ") + degrees + String("\xB0"));
  steeringMotor.write(degrees);
}

void setSpeed(int speed) {
  int degrees = (speed + 100) / ((double) 200 / 180);
  writeLine(1, String("Speed: ") + speed + String("% ") + degrees + String("\xB0"));
  throttleMotor.write(degrees);
}

void loop() {
  if (Serial.available() > 0) {
    // If the JSON object is more complex, you need to increase this value.
    StaticJsonBuffer<200> jsonBuffer;

    JsonObject& json = jsonBuffer.parse(Serial);

    json.printTo(Serial);
    Serial.println();

    if (!json.success()) {
      Serial.println("Unable to parse JSON!");
      return;
    }
        
    const String type = json["type"];
    if (type == "steering") {
      setSteering(json["angle"]);
    } else if (type == "throttle") {
      setSpeed(json["speed"]);
    } else {
      Serial.println(String("Unknown message type '") + type + String("'"));
    }
  }
}
