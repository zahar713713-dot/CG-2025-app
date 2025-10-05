#include <WiFi.h>
#include <AsyncTCP.h>
#include <ESPAsyncWebServer.h>

#include <LiquidCrystal_I2C.h>
#define WINDOW_SIZE 5

const int ecgPin = 36;

// Параметры точки доступа
const char* ssid = "WHAT";      // Имя сети (SSID)
const char* password = "12345678";    // Пароль (минимум 8 символов)

AsyncWebServer server(80);
AsyncWebSocket ws("/ws");  // WebSocket endpoint

//filtering and smoothing
float buf[WINDOW_SIZE];
int idx = 0;
float sum = 0;

//peak detection
float lastValue = 0;
unsigned long lastPeakTime = 0;
int heartRate = 0;
const int peakThreshold = 100;

unsigned long lastSampleTime = 0;

unsigned long lastPrint = 0;
unsigned long lastSend = 0;


#define BUFFER_SIZE 100  // Размер буфера

float send_buffer[BUFFER_SIZE];
int bufferCount = 0;

String arrayToString(float arr[], int size, int decimals = 2) {
  String result = "[";
  for (int i = 0; i < size; i++) {
    result += String(arr[i], decimals);
    if (i < size - 1) {
      result += ",";
    }
  }
  result += "]";
  return result;
}

// Добавление значения в буфер
bool addToBuffer(int value) {
  if (bufferCount < BUFFER_SIZE) {
    send_buffer[bufferCount] = value;
    bufferCount++;
    return true;
  }
  return false; // буфер полон
}

// Чтение значения по индексу
int readFromBuffer(int index) {
  if (index < bufferCount) {
    return send_buffer[index];
  }
  return -1; // ошибка
}

// Очистка буфера
void clearBuffer() {
  bufferCount = 0;
}

// Проверка статуса
bool isBufferEmpty() {
  return bufferCount == 0;
}

bool isBufferFull() {
  return bufferCount == BUFFER_SIZE;
}

LiquidCrystal_I2C lcd(0x27,16,2);

void notifyClients(const char* message) {
  ws.textAll(message);
}

void handleWebSocketMessage(void *arg, uint8_t *data, size_t len) {
  AwsFrameInfo *info = (AwsFrameInfo*)arg;
  if (info->final && info->index == 0 && info->len == len && info->opcode == WS_TEXT) {
    // Можно обрабатывать входящие сообщения от клиента, если нужно
    // Например: String msg = (char*)data;
  }
}

void onEvent(AsyncWebSocket *server, AsyncWebSocketClient *client,
             AwsEventType type, void *arg, uint8_t *data, size_t len) {
  switch (type) {
    case WS_EVT_CONNECT:
      Serial.println("Клиент подключился к WebSocket");
      break;
    case WS_EVT_DISCONNECT:
      Serial.println("Клиент отключился");
      break;
    case WS_EVT_DATA:
      handleWebSocketMessage(arg, data, len);
      break;
    default:
      break;
  }
}

float movingAverage(float newValue) {
  sum -= buf[idx];
  buf[idx] = newValue;
  sum += buf[idx];
  idx = (idx + 1) % WINDOW_SIZE;
  return sum / WINDOW_SIZE;
}

// Простой IIR high-pass фильтр (0.5 Гц при 250 Гц)
float alpha = 0.99; // зависит от частоты
static float lastOutput = 0;
static float lastInput = 0;

float highpass(float input) {
  float output = alpha * (lastOutput + input - lastInput);
  lastInput = input;
  lastOutput = output;
  return output;
}

void setup() {
  // initialize the serial communication:
  Serial.begin(115200);
  pinMode(LED_BUILTIN, OUTPUT);//built-in output
  pinMode(22, INPUT); // Setup for leads off detection LO +
  pinMode(23, INPUT); // Setup for leads off detection LO -
  pinMode(ecgPin, INPUT); 

  lastPrint = millis();

  Wire.begin(19, 18);
  lcd.init();
  lcd.backlight();

  // Настройка WebSocket
  ws.onEvent(onEvent);
  server.addHandler(&ws);

  // Запуск точки доступа
  WiFi.softAP(ssid, password);

  // Получение IP-адреса точки доступа
  IPAddress IP = WiFi.softAPIP();
  Serial.print("Точка доступа запущена. IP: ");
  Serial.println(IP);

  server.begin();
  Serial.println("WebSocket-сервер запущен");
}

float hp;

void loop() {
  hp = highpass(analogRead(ecgPin));
  float smoothed = movingAverage(hp);

  lastSampleTime = millis();

  float diff = smoothed - lastValue; // приращение
  lastValue = smoothed;

  // Детекция R-пика: резкий положительный скачок
  if (diff > peakThreshold) {
    unsigned long currentTime = millis();
    
    // Игнорируем пики, идущие слишком часто 
    if (currentTime - lastPeakTime > 400) {
      // Расчёт ЧСС
      unsigned long rrInterval = currentTime - lastPeakTime; // в миллисекундах
      if (rrInterval > 0) {
        heartRate = 60000 / rrInterval; // 60 сек = 60000 мс
      }
      lastPeakTime = currentTime;

      // Отладка: мигаем LED при каждом пике
      digitalWrite(LED_BUILTIN, !digitalRead(LED_BUILTIN));
    }
  }

  // Отправка данных в Serial Plotter (опционально)
  
  Serial.print(smoothed);
  Serial.print(",");
  Serial.println(heartRate);

//  unsigned long currentTime = millis();
//  if (currentTime - lastPrint > 500){
//    lcd.clear();
//    lcd.print(String(heartRate));//  }

  lastSend = millis();
  String message = String(smoothed);

  if (!isBufferFull()) 
  {
    addToBuffer(smoothed);
  }
  else
  {
//    notifyClients(message.c_str());
    notifyClients(arrayToString(send_buffer, BUFFER_SIZE).c_str());
    clearBuffer();

    lcd.clear();
    lcd.print(String(heartRate));
  }
  
  
  //Wait for a bit to keep serial data from saturating
  delay(4);
}
