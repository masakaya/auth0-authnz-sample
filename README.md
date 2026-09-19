# auth0-authnz-sample

既存 EC 基盤から認証基盤を切り出すための参照実装です。Auth0 でログイン(ID / パスワード、パスキー)を行い、Angular の SPA、Android のネイティブアプリ(アプリ内 WebView で同じ Angular を表示)、Spring Boot の API(既存 API の代役)で認証と認可を確認します。

- ログイン画面は Auth0 の Universal Login。ネイティブアプリではアプリ内 WebView ではなく Custom Tab で開く
- ネイティブアプリで1回ログインすれば、WebView 内の Angular は再ログインなしで API を呼べる(アクセストークンだけを、オリジンを検証するブリッジで渡す)
- API は Auth0 のアクセストークン(JWT)を検証し、呼べるかどうかはコントローラのアノテーションで判定する。顧客の区分はゲスト(未ログイン)/ ロール1 / ロール2、加えてブランドへの帰属(1人が複数ブランドに属しうる)

## ディレクトリ構造

```
settings.gradle.kts        Gradle の composite build(backend を常に、android は明示したときだけ取り込む)
gradle/libs.versions.toml  依存の版の一元管理
compose.yaml               PostgreSQL(顧客台帳。データは保持しない)
backend/                   Spring Boot の API(Resource Server)
  src/main/resources/db/migration/   Flyway のマイグレーション
frontend/                  Angular の SPA(npm 管理)
android/                   Android アプリ
  bridge-core/             ブリッジの OS 非依存部分(純 Kotlin、単体テストあり)
  app/                     アプリ本体
auth0/                     Auth0 のテナント設定(Deploy CLI)、post-login Action、操作手順
.github/workflows/         CI(backend / frontend / auth0-actions / Android)
```

## 使用技術

| 対象 | 技術と版 |
|---|---|
| ビルド | Gradle 9.7.1(Kotlin DSL、Wrapper)、Java 21 |
| backend | Spring Boot 4.1.1、Spring Security(OAuth2 Resource Server、メソッドセキュリティ)、PostgreSQL 17、Flyway 13.7.0、Testcontainers |
| frontend | Angular 22、`@auth0/auth0-angular` 2.12、Vitest、Node.js 22 |
| android | Android Gradle Plugin 9.4.1(内蔵の Kotlin 2.2.10)、compileSdk 36 / minSdk 26、Auth0.Android 4.1.0、androidx.webkit 1.17.0 |
| Auth0 | Universal Login(Identifier First)、パスキー、RBAC、post-login Action、Auth0 Deploy CLI 9、Auth0 CLI |
| CI | GitHub Actions |

## 確認方法

必要なもの: Java 21、Node.js 22、Docker(Compose v2)。Android SDK は不要です(APK は CI でビルドします)。

### backend

```bash
./gradlew :backend:test          # データベースや Auth0 への接続は不要(台帳のテストは Testcontainers が Docker を使う)
./gradlew :backend:bootRun       # http://localhost:8080
```

Auth0 のテナントに向けるときは、環境変数を設定してから起動します(または `backend/src/main/resources/application-local.yml.example` を `application-local.yml` にコピーして `--args='--spring.profiles.active=local'`)。

```bash
export AUTH0_ISSUER='https://<ログイン画面のドメイン>/'   # 末尾のスラッシュまで含める。カスタムドメインを使うならそれ
export AUTH0_AUDIENCE='https://api.example.com'          # Auth0 に登録した API の Identifier
```

| エンドポイント | 呼べる条件 |
|---|---|
| `GET /api/public` | 誰でも(ゲストを含む) |
| `GET /api/private` | ログイン済み |
| `GET /api/brand-a/offers` / `GET /api/brand-b/offers` | そのブランドに属する |
| `GET /api/brand-a/role2-only` | ブランド A に属し、かつロール2 |
| `GET /api/admin` | 権限 `read:admin` を持つ |

未ログインでの応答(実測):

```bash
curl -i http://localhost:8080/api/public                                   # 200
curl -i http://localhost:8080/api/private                                  # 401
curl -i -H 'Authorization: Bearer not-a-jwt' http://localhost:8080/api/public   # 401(不正なトークンはゲスト扱いにしない)
```

権限の出どころは設定 `app.auth.authority-source` で切り替えます。

| 値 | 権限の導き方 | 停止中の顧客 |
|---|---|---|
| `claims`(既定) | アクセストークンのクレーム(ブランド、顧客ロール、`permissions`) | API 側では確認しない(Auth0 側でのブロックに依存) |
| `ledger` | トークンの `sub` で顧客台帳(PostgreSQL)を引く。`permissions` だけはトークンから | ステータスが `active` でなければ全リクエストを 403(`{"error":"customer_not_active"}`) |

`ledger` は、ブランドや顧客ロールの正本を既存基盤側に残す構成に相当します。試すときは、下の手順でデータベースを用意してから `APP_AUTH_AUTHORITY_SOURCE=ledger ./gradlew :backend:bootRun` で起動します。

### データベース(顧客台帳)

データはメモリ(tmpfs)に置き、永続ボリュームを持ちません。いつ捨てても、リポジトリのファイルから作り直せます。

```bash
docker compose up -d                 # PostgreSQL。ホスト側のポートは 54329
./gradlew :backend:flywayMigrate     # スキーマとサンプル顧客 4 件を作成
./gradlew :backend:flywayInfo        # 適用状況の確認
./gradlew :backend:flywayClean       # 中身だけ消す
docker compose down -v               # 完全に捨てる。次の up -d では空のデータベースになる
```

アプリの起動時にマイグレーションは行いません。コマンドでの実行が正です。接続先を変えるときは `DB_URL` / `DB_USER` / `DB_PASSWORD` を設定します。

サンプル顧客の `sub` は `auth0|sample-...` という仮の値です。実際のユーザーで試すときは、Auth0 のユーザー ID に合わせて `customer` テーブルの `subject` を書き換えます。台帳に無い `sub` は、初回アクセス時にブランドもロールも持たない顧客として登録されます。

### frontend

```bash
cd frontend
npm ci
npm test
cp src/environments/environment.local.ts.example src/environments/environment.local.ts
# environment.local.ts に Auth0 のドメイン(カスタムドメインを使うならそれ)、SPA の Client ID、audience を記入(コミットされません)
npm run start:local                  # http://localhost:4200
```

画面からログイン / ログアウトと、上の表のエンドポイントの呼び出しを試せます。トークンはメモリにだけ保持します。ネイティブアプリの WebView の中で開かれたときは、Auth0 の SDK を使わず、ネイティブアプリからブリッジ経由でトークンを受け取ります(ログインボタンは表示しません)。

### android

開発機に Android SDK が無くても構いません。APK は GitHub Actions の `Android` ワークフローがビルドし、実行結果の Artifacts(`app-debug`)からダウンロードできます。

```bash
gh run list --workflow Android --limit 1
gh run download <run-id> -n app-debug
adb install -r app-debug.apk
```

Auth0 の設定値を埋め込んだ APK が必要なときは、SDK のある環境で `android/local.properties.example` を `android/local.properties` にコピーして値を入れ、次を実行します。

```bash
./gradlew -PrequireAndroid :android:app:assembleDebug
./gradlew -p android/bridge-core test        # ブリッジの単体テスト(SDK 不要)
```

`http://` のオリジンを WebView で開けるのは debug ビルドだけです。release ビルドは、`webApp.url` が `https://` でなければ失敗します。

### リモートの開発環境へ手元の PC からつなぐ

backend と frontend をリモートの開発機で動かし、手元の PC のブラウザや実機から確認する方法です。

```bash
# 手元の PC で。リモートの 4200(frontend)と 8080(backend)を手元の同じポートへ転送する
ssh -L 4200:localhost:4200 -L 8080:localhost:8080 <リモートの開発機>
```

手元のブラウザで `http://localhost:4200` を開きます。Auth0 から見たオリジンが `http://localhost:4200` になるので、Auth0 の Application の設定はローカル開発のときと同じです。

Android の実機で確認するときは、実機を手元の PC に USB でつなぎ、さらに実機のポートを手元の PC へ転送します。

```bash
adb reverse tcp:4200 tcp:4200
adb reverse tcp:8080 tcp:8080
```

これで実機の WebView からも `http://localhost:4200` で Angular が開き、ブリッジの許可オリジンも同じ値のままで済みます。

### パスキーの試し方

パスキーの登録と認証は Auth0 のログイン画面の上で行われるので、Android アプリが無くても、ブラウザから frontend を開けば試せます。認証器には、Windows Hello、Touch ID、スマートフォンのパスキー(QR コード)、Chrome DevTools の仮想認証器が使えます。手順は [`auth0/README.md`](auth0/README.md) の「パスキーの試し方」を参照してください。

## Auth0 の情報

必要な設定は `auth0/tenant.yaml` にまとめてあり、Auth0 Deploy CLI で反映します。手作業で残るもの、反映の手順、Auth0 CLI での操作例(テストユーザーの作成、ブランドと顧客ロールの設定、テスト用トークンの取得、ログの確認)は [`auth0/README.md`](auth0/README.md) にあります。

| 対象 | 設定 |
|---|---|
| ログイン画面 | New Universal Login、Identifier First(パスキーの前提条件) |
| Database 接続 | 識別子はメールアドレス。パスワードとパスキーの両方を有効 |
| API | RBAC を有効にし、`permissions` をアクセストークンに載せる。アクセストークンの有効期間は 900 秒 |
| Application | OS ごとに1つ(SPA、Android)。リフレッシュトークンはローテーション。Android のコールバックは `https://`(App Links) |
| post-login Action | ユーザーの `app_metadata.brands` と `app_metadata.customer_role` を、名前空間つきのクレームとしてアクセストークンに載せる |

先に決めておくこと:

- **カスタムドメインを使うかどうか。** パスキーはログイン画面のドメインに紐づくため、パスキーを有効にした後でカスタムドメインへ変えると、登録済みのパスキーがすべて使えなくなります。使う場合は、テナント設定を反映する前にドメインの検証を済ませ、各アプリのドメインをすべてカスタムドメインに揃えます(手順と設定先の一覧は [`auth0/README.md`](auth0/README.md) の「カスタムドメインを使う場合」)。カスタムドメインそのものは、このリポジトリにコミットしません。
- **契約プラン。** このサンプルは上位プラン限定の機能に依存しない構成にしていますが、RBAC と Application 数の上限はプランで確認してください。

各アプリに設定する値(いずれもコミットしません):

| アプリ | 設定先 | 値 |
|---|---|---|
| backend | 環境変数、または `application-local.yml` | `AUTH0_ISSUER`、`AUTH0_AUDIENCE` |
| frontend | `frontend/src/environments/environment.local.ts` | ドメイン、SPA の Client ID、audience |
| android | `android/local.properties` | ドメイン、Android の Client ID、audience、WebView で開く URL |
| Deploy CLI | `auth0/config.json` と環境変数 `AUTH0_CLIENT_SECRET` | テナントのドメイン、Deploy CLI 用 Application の Client ID |

> このリポジトリの Auth0 関連のコマンドは、実テナントに対してはまだ実行していません(単体テストとビルドまでを確認済み)。
