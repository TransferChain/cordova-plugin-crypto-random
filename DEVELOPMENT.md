# TransferChain Random — geliştirici kılavuzu

**Sürüm:** 1.0.0. **Amaç:** istenen uzunlukta, işletim sisteminin kriptografik
rastgelelik kaynağından byte üretmek. Parola, IV veya salt politikasını çağıran
katman belirler; plugin yalnızca güvenli byte üretim primitive'idir.

Kurulum ve kullanım: [README](README.md).

## Kaynak haritası

| Dosya                              | Sorumluluk                                   |
| ---------------------------------- | -------------------------------------------- |
| [plugin.xml](plugin.xml)           | Random service / CryptoKit.Random global     |
| [www/Random.js](www/Random.js)     | Promise ve ArrayBuffer → Uint8Array dönüşümü |
| [Android](src/android/Random.java) | length doğrulaması, SecureRandom, callback   |
| [iOS](src/ios/Random.swift)        | length doğrulaması, SecRandomCopyBytes       |

## API sözleşmesi

```js
const bytes = await CryptoKit.Random.randomBytes(32)
try {
  await consumeBytes(bytes)
} finally {
  bytes.fill(0)
}
```

Örnek deviceready sonrasında çalışır. Girdi ve consumer fonksiyonları çağıran
tarafından sağlanır; sahip olunan secret bufferlar işlem bitince temizlenir.
Ayrıntılar: [API](docs/API.md).

Çağrı deviceready sonrasında yapılır. Doğrudan Cordova paketi tüketicisi için
eşdeğer API window.CryptoKit.Random.randomBytes(length) şeklindedir.

| Alan                          | Sözleşme                                                |
| ----------------------------- | ------------------------------------------------------- |
| length                        | Zorunlu integer number; 1–1.048.576 byte                |
| Sonuç                         | Promise<Uint8Array>; tam length uzunluğunda             |
| Boş/negatif/kesirli/NaN değer | INVALID_ARGUMENT                                        |
| Native kaynak hatası          | OPERATION_FAILED; kaynak yetersizliğinde RESOURCE_LIMIT |

length bit sayısı veya karakter sayısı değildir. 32 değeri 32 byte üretir.
Başarılı sonuç Uint8Array olarak döner; string/Base64 sonucu üretilmez.

## İşlem nasıl ilerler?

1. www/Random.js randomBytes action'ına [{ length }] gönderir.
2. Android işi cordova.getThreadPool() üzerinde çalıştırır. iOS Cordova
   background çalıştırma mekanizmasını kullanır.
3. Native taraf integer ve sınır kontrolünden sonra çıktı buffer'ını oluşturur.
4. Android unseeded SecureRandom, iOS SecRandomCopyBytes kullanır.
5. Cordova binary callback'i tamamlar; JS Uint8Array döndürür.

Secure Storage referansındaki OS CSPRNG yaklaşımı korunur. Doğrudan /dev/urandom
açan ek dosya katmanı, Math.random veya timestamp seed'i yoktur. OS kaynağı
başarısızsa daha zayıf bir generator'a fallback yapılmaz.

## Geliştirirken korunacak noktalar

Üst sınır WebView/native bellek tahsislerini sınırlar. Artırmadan önce aynı anda
yapılan çok sayıda isteğin toplam bellek etkisini ölçün. Tek isteğin sınırlı
olması global bellek kotası olduğu anlamına gelmez.

JS katmanında ayrı Base64 dönüşümü eklemeyin. Cordova'nın dahili taşımasını
uygulama API'sine sızdırmayın. Byte dizisini metin gibi işlemeyin; karakter
encoder'ı rastgele byte taşıma yöntemi değildir.

Native iş background üzerinde yürütülür. Çağıran eşzamanlı istek sayısını ve
toplam bellek bütçesini yönetir; plugin bir JS iş kuyruğu sunmaz.

## Test ve değişiklik örneği

Yeni bir uzunluk sınırı için [bridge](tests/js/bridge.test.js) ve
[native testleri](tests/native/RandomTests.java) birlikte güncellenmelidir.
Platform harness gereksinimleri: [TESTING](docs/TESTING.md).

Doğrulanacak örnekler: 1 ve maksimum uzunluk, 0/negatif/kesirli değer, doğru
Uint8Array uzunluğu, native scheduling hatası. İki çıktının farklı olması
CSPRNG'nin güvenli olduğunu tek başına kanıtlamaz; üretici OS API'si
korunmalıdır.
