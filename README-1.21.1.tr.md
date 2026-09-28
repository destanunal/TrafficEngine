# Traffic Engine 1.21.1

Bu klasör, 1.20.1 kaynaklarından ayrılmış `mc-1.21.1-port` Git dalıdır. Eski
`TrafficEngine-master` çalışma klasörü ve onun Fabric/Forge 1.20.1 sürümleri
yerinde durur. 1.21.1 için aynı ortak kodu kullanan iki çıktı üretilir:

| Minecraft | Yükleyici | Çıktı |
| --- | --- | --- |
| 1.21.1 | Fabric | `fabric/build/libs/trafficengine-fabric-1.5+1.21.1.jar` |
| 1.21.1 | NeoForge | `neoforge/build/libs/trafficengine-neoforge-1.5+1.21.1.jar` |

Bir kurulumda yalnızca kendi yükleyicisine ait JAR dosyasını kullanın. Her iki
JAR, DragonLib 1.21.1 beta sürümünü içerir. Fabric için ayrıca Fabric API,
Architectury API ve Forge Config API Port gerekir. NeoForge için Architectury
API gerekir. Mod ve yükleyici 1.21.1 sürümünde Java 21 kullanır.

## Derleme

Java 21 kurulu bir terminalde:

```powershell
.\gradlew.bat :fabric:build :neoforge:build
```

Yayımlanacak dosyalar yukarıdaki tabloda belirtilen, `-dev-shadow` içermeyen
JAR dosyalarıdır. Fabric ve NeoForge geliştirme istemcileri ana menüye kadar
açılarak kontrol edildi. Dünya yükleme ve mevcut dünyadan yükseltme henüz
doğrulanmadı.

## 1.20.1 dünyası

1.20.1 dünyasını ve modlar klasörünü önce yedekleyin. Ardından yedeğin bir
kopyasını 1.21.1 ile, aynı yükleyici türünü ve 1.21.1 uyumlu modları kullanarak
açın. Minecraft dünyayı yeni sürüme yükseltir; yükseltilmiş dünya 1.20.1'e
geri açılmamalıdır. Traffic Engine kimliği `trafficengine` olarak korunmuştur,
ancak 1.20.1 dünyasındaki bütün blok, eşya ve diğer mod verilerinin sorunsuz
geçeceği henüz doğrulanmadığı için asıl dünyayı doğrudan açmayın.
