# Auth0 の設定と操作

このディレクトリは、サンプルが前提とする Auth0 テナントの設定をファイルで持ち、コマンドで反映できるようにしたものです。

> **実行状況**: ここに書いた Deploy CLI / Auth0 CLI のコマンドは、公式ドキュメントの現行版で書式を確認したものですが、**実テナントに対してはまだ実行していません**。Action の単体テスト(`npm test`)だけが実行済みです。実テナントで確認できたものから、この注記を更新します。

## 構成

| パス | 内容 |
|---|---|
| `tenant.yaml` | テナント設定(API、Application、Database 接続、ロール、Action、ログインフロー、ログイン画面の方式) |
| `actions/add-customer-claims/code.js` | post-login Action。`app_metadata` のブランド帰属と顧客ロールをアクセストークンのクレームに載せる |
| `actions/add-customer-claims/code.test.js` | Action の単体テスト(`node:test`) |
| `config.json.example` | Deploy CLI の設定例。`config.json` にコピーして使う(`config.json` はコミットしない) |

## 使うツール

| ツール | 役割 | 導入 |
|---|---|---|
| Auth0 Dashboard | プランの確認、カスタムドメイン、Deploy CLI 用 Application の作成 | ブラウザ |
| [Auth0 Deploy CLI](https://github.com/auth0/auth0-deploy-cli)(`a0deploy`) | `tenant.yaml` をテナントへ反映(import)、テナントの現状を書き出し(export) | このディレクトリで `npm install` |
| [Auth0 CLI](https://github.com/auth0/auth0-cli)(`auth0`) | テストユーザーの作成、テスト用トークンの取得、ログの確認 | 公式の手順でバイナリを導入 |
| Management API | 上の2つで足りない操作 | `curl` |
| Terraform(auth0 プロバイダ) | 本番の IaC を Terraform に統一している場合の選択肢 | このサンプルでは使わない。Deploy CLI と二重管理にしないこと |
| Auth0 MCP Server | AI エージェントからテナントを操作する | 任意。テナントへの書き込み権限をエージェントに渡すことになるので、開発用テナント限定・最小限のスコープで使う。このリポジトリの設定には含めていない |

## 1. Dashboard で行う手作業

`tenant.yaml` では表現できない、または先に決める必要があるものです。

1. **プランの確認**(Settings → Subscription)。RBAC が使えるか、Application 数の上限もあわせて確認します。
2. **カスタムドメインを使うかを最初に決める。** パスキーはログイン画面のドメインに紐づくため、パスキーを有効にした後でカスタムドメインへ変えると、登録済みのパスキーがすべて使えなくなります。本番で使うなら、パスキーの有効化より前に設定します。サンプルの検証だけならテナント既定のドメインで構いません。
3. **Deploy CLI 用の Application を作る。** Applications → Create Application → Machine to Machine。Auth0 Management API を選び、次のスコープを許可します。
   `read:clients` `create:clients` `update:clients` `read:client_keys` `read:resource_servers` `create:resource_servers` `update:resource_servers` `read:connections` `create:connections` `update:connections` `read:roles` `create:roles` `update:roles` `read:actions` `create:actions` `update:actions` `read:triggers` `update:triggers` `read:prompts` `update:prompts`
   (削除を無効にしているので `delete:*` は付けません。)

## 2. Deploy CLI で設定を反映する

```bash
cd auth0
npm install
cp config.json.example config.json      # config.json は .gitignore 済み
# config.json の AUTH0_DOMAIN / AUTH0_CLIENT_ID と AUTH0_KEYWORD_REPLACE_MAPPINGS を埋める
export AUTH0_CLIENT_SECRET='...'         # Client Secret はファイルに書かず環境変数で渡す
npm run import
```

`AUTH0_KEYWORD_REPLACE_MAPPINGS` の値:

| キー | 内容 | 例 |
|---|---|---|
| `AUTH0_DOMAIN` | テナントのドメイン(カスタムドメインを使うならそれ) | `your-tenant.us.auth0.com` |
| `API_AUDIENCE` | API の Identifier。backend の `AUTH0_AUDIENCE` と同じ値 | `https://api.example.com` |
| `CLAIM_NAMESPACE` | カスタムクレームの名前空間。backend の `app.auth.claim-namespace` と同じ値(末尾スラッシュなし) | `https://authnz.example.com` |
| `SPA_ORIGIN` | Angular のオリジン | `http://localhost:4200` |
| `ANDROID_PACKAGE_NAME` | Android アプリの applicationId | `com.example.authnz.app` |
| `ANDROID_SHA256_FINGERPRINT` | APK の署名証明書の SHA-256(`keytool -list -v -keystore <keystore>` で表示) | `AA:BB:...` |

注意点:

- **削除は無効**(`AUTH0_ALLOW_DELETE: false`)。`tenant.yaml` に無いリソースは消えません。`true` にすると、YAML に無い Application や接続が削除されます。
- **対象を限定**(`AUTH0_INCLUDED_ONLY`)。このファイルで管理する種類のリソースだけを読み書きします。
- **ログインフローは置き換わる。** `triggers.post-login` は一覧ごと置き換えるので、既にほかの Action をログインフローに置いているテナントでは、それも `tenant.yaml` に書き足してください。
- **ログイン画面の方式が変わる。** `prompts` でテナント全体を New Universal Login + Identifier First に切り替えます(パスキーの前提条件)。ほかのアプリと共用しているテナントでは影響を確認してください。
- 反映後に `npm run export` を実行すると、テナントの現状が `exported/` に書き出されます。`tenant.yaml` との差分で、意図どおり反映されたかを確認できます。
- パスキー関連の設定(`authentication_methods`、`passkey_options`)が反映されない場合は、Dashboard の Authentication → Database → `authnz-sample-users` → Authentication Methods で Passkey を有効にしてください。

反映後、Dashboard の Applications で `authnz-sample-spa` と `authnz-sample-android` の Client ID を確認し、frontend と Android のローカル設定に入れます(コミットしません)。

## 3. 設定の中身

| 対象 | 設定 | 理由 |
|---|---|---|
| API `authnz-sample-api` | RBAC 有効、`permissions` をアクセストークンに追加 | backend が `permissions` クレームを権限に写像する |
| 同上 | アクセストークンの有効期間 900 秒(Auth0 の既定は 86,400 秒) | ブランド帰属や顧客ロールの変更は、新しいトークンが発行されるまで API に届かないため |
| Application | OS ごとに1つ(SPA / Android)。リフレッシュトークンはローテーション | コールバック、端末設定、失効が Application 単位のため。iOS / Windows も同じ形で足す |
| Android | コールバックは `https://`(App Links) | カスタムスキームは他のアプリに横取りされうる |
| Database 接続 `authnz-sample-users` | 識別子はメールアドレス、パスワードとパスキーの両方を有効 | 専用の接続にして、テナント既定の接続を変えない |
| ロール `authnz-sample-admin` | `read:admin` | `/api/admin` を呼べるユーザーを分ける |
| Action `add-customer-claims` | `app_metadata.brands` と `app_metadata.customer_role` をクレームに載せる | 形式が不正な値は落とす。ブランドのクレームは空でも必ず付ける |

クレームと backend の権限の対応:

| `app_metadata` | アクセストークンのクレーム | backend の権限 |
|---|---|---|
| `brands: ["brand-a"]` | `<namespace>/brands: ["brand-a"]` | `ROLE_BRAND_A` |
| `customer_role: "role1"` / `"role2"` | `<namespace>/customer_role` | `ROLE_ROLE1` / `ROLE_ROLE2` |
| (ロールの Permissions) | `permissions: ["read:admin"]` | `read:admin` |
| (トークンなし) | — | `ROLE_GUEST` |

Action の単体テスト:

```bash
cd auth0
npm test
```

## 4. Auth0 CLI での操作

```bash
auth0 login                          # ブラウザでテナントにログイン
auth0 tenants list
```

テストユーザーの作成(3名。ブランドと顧客ロールの組み合わせを変える):

```bash
auth0 users create --connection-name authnz-sample-users \
  --email brand-a-role1@example.com --name "Brand A / role1" --password '<十分に強いパスワード>'
auth0 users search --query 'email:"brand-a-role1@example.com"'     # user_id を確認
```

`app_metadata` の設定(Management API を Auth0 CLI 経由で呼ぶ):

```bash
auth0 api patch "users/<user_id>" --data '{"app_metadata":{"brands":["brand-a"],"customer_role":"role1"}}'
auth0 api patch "users/<user_id>" --data '{"app_metadata":{"brands":["brand-b"],"customer_role":"role2"}}'
auth0 api patch "users/<user_id>" --data '{"app_metadata":{"brands":["brand-a","brand-b"],"customer_role":"role2"}}'
```

ロールの付与:

```bash
auth0 roles list                     # authnz-sample-admin の ID を確認
auth0 users roles assign "<user_id>" --roles "<role_id>"
```

テスト用のアクセストークンを取得して backend を呼ぶ:

```bash
auth0 test token <SPA の Client ID> --audience https://api.example.com --scopes openid
curl -i -H "Authorization: Bearer <access_token>" http://localhost:8080/api/private
```

`auth0 test token` を使うには、対象の Application の Allowed Callback URLs に `http://localhost:8484/login/callback` が必要です(CLI が追加を提案します)。取得したトークンは <https://jwt.io> などに貼らず、手元でデコードしてクレームを確認してください。

```bash
cut -d. -f2 <<< "<access_token>" | base64 -d 2>/dev/null
```

ログの確認(ログイン失敗や Action のエラーを追う):

```bash
auth0 logs tail
auth0 logs list --filter 'type:f'    # 失敗したログインだけ
```

## 5. パスキーの試し方

パスキーの登録と認証は Auth0 のログイン画面(Universal Login)の上で行われるので、Android アプリが無くても、ブラウザから Angular を開けば試せます。

1. メールアドレスとパスワードでサインアップ、またはログインする。
2. ログインの途中でパスキーの作成を勧める画面が出るので、作成する。
3. ログアウトし、もう一度ログインする。メールアドレスを入れたあと、パスキーでのログインが選べる。

使える認証器は、Windows Hello、Touch ID、スマートフォンのパスキー(QR コードを読み取る)、Chrome DevTools の仮想認証器(WebAuthn タブ)です。リモートの開発機で Angular を動かしている場合は、SSH のポート転送で手元のブラウザから `http://localhost:4200` として開いてください(手順はリポジトリ直下の README を参照)。
