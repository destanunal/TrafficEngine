# Traffic Engine yayınlama

GitHub Actions içindeki **Publish Traffic Engine** akışı iki kaynak dalını sabit commitlerle derler:

- Minecraft 1.20.1: Fabric ve Forge
- Minecraft 1.21.1: Fabric ve NeoForge

## Bir defalık kurulum

1. [Depo anahtarları](https://github.com/destanunal/TrafficEngine/settings/secrets/actions) sayfasını açın.
2. **New repository secret** ile **CURSEFORGE_TOKEN** ekleyin. Değerini [CurseForge API Tokens](https://legacy.curseforge.com/account/api-tokens) sayfasından alın.
3. Modrinth kullanacağınızda **MODRINTH_TOKEN** ekleyin. [Modrinth Personal Access Tokens](https://modrinth.com/settings/pats) sayfasında **Create versions**, **Read versions**, **Write versions** izinlerini seçin.

Anahtarları sohbetlere veya kaynak dosyalara yazmayın. Proje kimlikleri yapılandırılmıştır:
CurseForge **1654475**, Modrinth **GkM5npCP**.

## Kullanım

1. [Actions](https://github.com/destanunal/TrafficEngine/actions/workflows/release.yml) sayfasını açın ve **Run workflow** seçin. Varsayılan dal **mc-1.20.1** olarak kalabilir.
2. **version** iki daldaki gradle.properties sürümüyle aynı olmalıdır.
3. **mode = build** yalnızca dört dağıtımı hazırlar; site anahtarı istemez ve hiçbir dosya yayımlamaz.
4. **mode = publish** derleme ve kontrollerden sonra yükler. **targets** alanında **curseforge**, **modrinth** veya **both** seçin. Modrinth projesi onay beklerken CurseForge'u bağımsız kullanabilirsiniz.
5. **changelog** alanında yayın notlarını kontrol edin. Yeni sürümde başlıktaki sürümü de değiştirin.
6. **Run workflow** düğmesine basın.

Tamamlanan çalışmanın **Artifacts** bölümünde dört son kullanıcı JAR'ı, SHA-256 değerleri ve kullanılan kaynak commitleri bulunur. Dosyalar 14 gün saklanır.

Bir yükleme başarısız olursa **Re-run failed jobs** kullanın. Başarılı yüklemeler böylece tekrar gönderilmez; bütün çalışmayı tekrar çalıştırmak aynı sürümü yeniden yüklemeye çalışabilir.

Kaynak kod veya akış dosyası düzeltildiyse yeni commitleri kullanmak için **Run workflow** ile yeni çalışma başlatın. **Re-run failed jobs**, önceki çalışmanın sabit kaynak commitlerini ve akışını tekrar kullanır.

Sitelerin inceleme/onay süreci yükleme tamamlandıktan sonra da sürebilir.

## Geliştirme

Yayıncı ayrı bir Gradle projesidir; normal compileJava, oyun kodu ve çalışma zamanı bağımlılıkları değişmez.
Akış yalnızca elle başlatılır. Push ve pull request olayları yayın tetiklemez.
Changelog dosyası kaynak depoya eklenmez; formdaki metin geçici yayın planına yazılır.
