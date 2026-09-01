# Spring Feign Saga Demo

Tamamlanan Feign `POST`, `PUT` ve `PATCH` çağrılarını kaydeden; yerel Spring
transaction rollback olduğunda başarılı uzak çağrıları ters sırada compensate
eden Oracle tabanlı çalışan demo.

## İçerik

- `@SagaTransactional` ile saga yaşam döngüsü
- Spring `TransactionSynchronization` ile gerçek commit/rollback sonucu
- Feign `Client` decorator ile request/response kaydı
- Varsayılan `original-url + /compensation` sözleşmesi
- Business transaction'dan bağımsız saga kayıtları (`REQUIRES_NEW`)
- Oracle `FOR UPDATE SKIP LOCKED` ile çoklu pod güvenli task claim
- Süreli lease (`LOCKED_AT`, `LOCKED_BY`) ve stale-lock recovery
- Exponential backoff + jitter
- Ters sıralı compensation
- `X-Saga-Step-Id` tabanlı idempotent demo servisleri
- `UNKNOWN` sonucu ile belirsiz uzak çağrıların ayrılması

## Gereksinimler

- Java 21
- Maven 3.9+
- Oracle Database

Spring Boot 3.5.0 ve Spring Cloud 2025.0.0 kullanılır.

## Oracle kullanıcısı

DBA kullanıcısıyla örnek kurulum:

```sql
CREATE USER saga_demo IDENTIFIED BY saga_demo;
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO saga_demo;
ALTER USER saga_demo QUOTA UNLIMITED ON USERS;
```

Uygulama ilk açılışta Flyway ile tabloları oluşturur.

## Çalıştırma

```bash
export ORACLE_URL='jdbc:oracle:thin:@//localhost:1521/FREEPDB1'
export ORACLE_USERNAME='saga_demo'
export ORACLE_PASSWORD='saga_demo'
mvn spring-boot:run
```

## Başarılı transaction

```bash
curl -i -X POST http://localhost:8080/api/payments \
  -H 'Content-Type: application/json' \
  -d '{
    "customerNo": "123456",
    "amount": 100.50,
    "failAfterRemoteCalls": false
  }'
```

Beklenen sonuç:

- `DEMO_PAYMENT` commit olur.
- Limit ve muhasebe demo operasyonları `ACTIVE` kalır.
- Saga durumu `COMPLETED` olur.

## Rollback ve compensation

```bash
curl -i -X POST http://localhost:8080/api/payments \
  -H 'Content-Type: application/json' \
  -d '{
    "customerNo": "123456",
    "amount": 100.50,
    "failAfterRemoteCalls": true
  }'
```

Akış:

1. Limit rezervasyonu başarılı olur.
2. Muhasebe kaydı başarılı olur.
3. Yerel ödeme kaydı yazılır.
4. Demo exception fırlatılır ve `DEMO_PAYMENT` rollback olur.
5. Muhasebe kaydı compensate edilir.
6. Muhasebe tamamlanınca limit rezervasyonu compensate edilir.
7. Saga `COMPENSATED` olur.

Worker varsayılan olarak beş saniyede bir çalıştığı için sonucu kısa bir gecikmeyle
görebilirsiniz:

```bash
curl http://localhost:8080/api/sagas
curl http://localhost:8080/api/sagas/{sagaId}
curl http://localhost:8080/api/sagas/{sagaId}/remote-operations
```

## Lock ve retry modeli

Worker önce Oracle transaction'ı içinde uygun task satırlarını
`FOR UPDATE SKIP LOCKED` ile seçer, `PROCESSING` yapar ve transaction'ı kapatır.
HTTP çağrısı yapılırken fiziksel DB lock tutulmaz. `LOCKED_AT` ve `LOCKED_BY`,
HTTP çağrısı boyunca kullanılan süreli lease bilgisidir.

Worker çökerse `lock-timeout-seconds` süresinden eski `PROCESSING` kaydı başka bir
pod tarafından tekrar alınır. Bu nedenle teslimat garantisi **at-least-once**'dır;
compensation endpoint'leri mutlaka idempotent olmalıdır.

Aynı sagada yalnızca en yüksek sıra numaralı tamamlanmamış step claim edilebilir.
Böylece birden fazla pod olsa bile compensation sırası korunur.

## Önemli üretim notları

- Request/response body'leri demo içinde 64 KB ile sınırlandırılmıştır. Üretimde
  kişisel veri ve credential alanları ayrıca maskelenmelidir.
- Network exception ve HTTP 5xx sonucu `UNKNOWN` kabul edilir. Bu stepler otomatik
  compensate edilmez; uzak serviste `stepId` ile sorgulama/reconciliation gerekir.
- HTTP 408, 429, 5xx ve bağlantı hataları retry edilir. Diğer 4xx cevapları manual
  intervention durumuna geçer.
- Demo katılımcıları anlaşılabilirlik için aynı uygulamada HTTP endpoint olarak
  çalışır. Gerçek sistemde limit ve muhasebe servisleri ayrı uygulamalardır.
- Compensation bir teknik `DELETE` değil, domain'e özel ters işlem olmalıdır.

## Paket yapısı

```text
saga/         Saga context, Feign decorator, store, worker ve retry
demo/client/  Limit ve muhasebe Feign client'ları
demo/api/     Request/response modelleri
demo/         Orchestrator ve idempotent participant endpoint'leri
```

## Test

```bash
mvn test
```

Unit testler Feign response body'nin yeniden oluşturulmasını, compensation URL
üretimini ve retry backoff sınırlarını kontrol eder. Oracle entegrasyon testi için
kuruma ait test Oracle şemasıyla ayrı bir CI profili eklenebilir.
