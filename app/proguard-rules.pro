# SAM TV OFICIAL — regras R8.
# Room, Media3, OkHttp, Coil e kotlinx.serialization já publicam suas regras de consumidor.
# O módulo :core usa apenas a API JsonElement (sem reflexão/serializers gerados).

# Mantém números de linha para relatórios de falha legíveis, mas oculta o nome do arquivo de origem.
-keepattributes LineNumberTable
-renamesourcefileattribute SourceFile

# OkHttp: avisos de plataformas opcionais.
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
