# CAP Java 認可（Authorization）検証プロジェクト

CAP Java における認可動作の仕様を実際に検証するためのサンプルプロジェクトです。

## 検証環境

| 項目 | バージョン |
|------|-----------|
| CAP Java (`cds-services-bom`) | **4.8.0** |
| Spring Boot | 3.5.11 |
| Java | 21 |

## 検証トピックと結果サマリー

| # | トピック | 検証内容 | 結果 |
|---|---------|---------|------|
| 1 | Bound Action のデフォルト権限 | `WRITE` ロールは action をカバーしない。action 名を `grant` に明示しないと呼び出せない | ✅ 確認 |
| 2 | Bound Action への `where` 条件の非引き継ぎ | `UPDATE` の `where` 条件は action には自動引き継ぎされない | ✅ 確認 |
| 3 | Bound Action の `where` 条件の適用可否 | 旧来「Java では無視される」とされていたが、**4.8.0 では適用される**（action の `where` が有効）。ただし `@restrict` を **エンティティ**に置き `grant` に action 名を指定した場合に限る | ⚠️ 想定と異なる |
| 4 | `CREATE` に対する `where` 条件（Java 4.0+） | CREATE 時に入力データの属性チェックが動作する | ✅ 確認 |
| 5 | Bound Function の権限制御と `where` 条件 | ロール制限は有効。**READ の `where` は function に引き継がれない**が、function grant の `where` は適用される（Action の Topic 3 と同様）。前提条件は Topic 3 と同じく `@restrict` をエンティティに置くこと | ✅ 確認 |

---

## 目次

- [セットアップ](#セットアップ)
  - [ドメインモデル](#ドメインモデル)
  - [権限制御アノテーション](#権限制御アノテーション)
  - [モックユーザー設定](#モックユーザー設定)
- [検証手順](#検証手順)
- [検証結果（詳細）](#検証結果)
  - [Topic 1: Bound Action のデフォルト権限](#topic-1-bound-action-のデフォルト権限)
  - [Topic 2: Bound Action への where 条件の非適用](#topic-2-bound-action-への-where-条件の非適用)
  - [Topic 3: Bound Action の where 条件の適用](#topic-3-bound-action-の-where-条件の適用cap-java-480)
  - [Topic 4: CREATE に対する where 条件](#topic-4-create-に対する-where-条件java-40)
  - [Topic 5: Bound Function の権限制御](#topic-5-bound-function-の権限制御)

---

## セットアップ

### ドメインモデル

```cds
// db/schema.cds
namespace com.example;
using { managed } from '@sap/cds/common';

entity Orders : managed {
  key ID     : UUID;
  title      : String(100);
  amount     : Decimal(10,2);
  region     : String(10);   // ← where条件での照合に使用
}
```

### 権限制御アノテーション

> **前提**: action/function に対して `where` を使うには、`@restrict` を **action/function に直接ではなくエンティティに**置き、`grant` に action/function 名を指定する必要があります。
> action/function に直接 `@restrict` を置いた場合は `@requires` 相当となり、`where` は使えません。
>
> ```cds
> // ❌ action に直接置いた場合 → where は使えない（@requires 相当）
> actions { action approve() @(restrict: [{ to: 'Admin', where: ... }]); }
>
> // ✅ エンティティに置き grant に action 名を指定 → where が使える
> @(restrict: [{ grant: 'approve', to: 'Admin', where: (region = $user.region) }])
> entity Orders ... actions { action approve(); }
> ```

```cds
// srv/order-service.cds
service OrderService @(requires: 'authenticated-user') {

  // Topic 1: WRITEはactionをカバーしない
  // addNoteはgrantに「action名を明示」しなければ呼び出し不可
  @(restrict: [
    { grant: 'READ' },
    { grant: 'WRITE',   to: 'Admin1' },
    { grant: 'addNote', to: 'Admin1' }  // ← 明示が必要
  ])
  entity OrdersTopic1 as projection on db.Orders
  actions { action addNote(note: String); }

  // Topic 2: UPDATEのwhere条件はactionに引き継がれない
  // process の grant に where を書かなければ、region問わず呼び出し可
  @(restrict: [
    { grant: 'READ' },
    { grant: ['CREATE', 'DELETE'], to: 'Admin2' },
    { grant: 'UPDATE',  to: 'Admin2', where: (region = $user.region) },
    { grant: 'process', to: 'Admin2' }  // ← whereなし
  ])
  entity OrdersTopic2 as projection on db.Orders
  actions { action process(); }

  // Topic 3: action grantのwhereの適用可否を検証
  // CAP Java 4.8.0では where が実際に適用される（後述）
  @(restrict: [
    { grant: 'READ' },
    { grant: ['CREATE', 'DELETE'], to: 'Admin3' },
    { grant: 'approve', to: 'Admin3', where: (region = $user.region) }
  ])
  entity OrdersTopic3 as projection on db.Orders
  actions { action approve(); }

  // Topic 4: Java 4.0+: CREATEのwhereで入力データを検証
  @(restrict: [
    { grant: 'READ' },
    { grant: ['CREATE', 'UPDATE', 'DELETE'], to: 'Admin4',
      where: (region = $user.region) }
  ])
  entity OrdersTopic4 as projection on db.Orders;

  // Topic 5: Bound Functionの権限制御
  // - READのwhereはfunctionに引き継がれない
  // - function grantのwhereは適用される（Actionと同様）
  @(restrict: [
    { grant: 'READ',         to: 'Admin5', where: (region = $user.region) },
    { grant: ['CREATE', 'DELETE'], to: 'Admin5' },
    { grant: 'getSummary',   to: 'Admin5', where: (region = $user.region) }
  ])
  entity OrdersTopic5 as projection on db.Orders
  actions {
    function getSummary() returns String;
  }
}
```

### モックユーザー設定

```yaml
# srv/src/main/resources/application.yaml
cds:
  security:
    mock:
      enabled: true
      users:
        - name: alice
          password: alice
          roles: [Admin1, Admin2, Admin3, Admin4, Admin5]
          attributes:
            region: [JP]       # $user.region = JP
        - name: bob
          password: bob
          roles: [Admin1, Admin2, Admin3, Admin4, Admin5]
          attributes:
            region: [US]       # $user.region = US
        - name: viewer
          password: viewer
          # ロールなし（authenticated-userのみ）
```

> **Note**: `cds-starter-cloudfoundry` 依存が必要。これがないと Spring Security が有効にならず、mock 認証が機能しない。
>
> ```xml
> <!-- srv/pom.xml -->
> <dependency>
>   <groupId>com.sap.cds</groupId>
>   <artifactId>cds-starter-cloudfoundry</artifactId>
> </dependency>
> ```

---

## 検証手順

```bash
# 起動
mvn spring-boot:run

# 各トピックの .http ファイルを VS Code REST Client で実行
# test/topic1-action-auth.http
# test/topic2-where-not-inherited.http
# test/topic3-action-where-ignored.http
# test/topic4-create-where.http
# test/topic5-function-auth.http
```

---

## 検証結果

### Topic 1: Bound Action のデフォルト権限

**`WRITE` ロールは action をカバーしない。action 名を `grant` に明示しないと呼び出せない。**

| テスト | ユーザー | 操作 | 結果 | 期待 |
|--------|---------|------|------|------|
| 1-A | alice（Admin1ロール） | `addNote` 呼び出し | **204** ✅ | 成功 |
| 1-B | viewer（ロールなし） | `addNote` 呼び出し | **403** ✅ | 拒否 |

**確認事項**: `grant: 'WRITE'` が付与されていても、action に対する権限は別途 `grant: 'addNote'` として明示が必要。

---

### Topic 2: Bound Action への `where` 条件の非適用

**`UPDATE` に設定した `where` 条件は、同エンティティの action には引き継がれない。**

| テスト | ユーザー | 操作 | 結果 | 期待 |
|--------|---------|------|------|------|
| 2-A | alice（region=JP） | JP Order を UPDATE | **200** ✅ | 成功（region一致） |
| 2-B | alice（region=JP） | US Order を UPDATE | **403** ✅ | 拒否（region不一致） |
| 2-C | alice（region=JP） | US Order の `process` 呼び出し | **204** ✅ | 成功（whereが引き継がれない） |

**確認事項**: `process` の grant には `where` を指定していないため、alice が region=US の Order に対しても呼び出せる。`UPDATE` の `where` 制限は action に自動引き継ぎされない。

---

### Topic 3: Bound Action の `where` 条件の適用（CAP Java 4.8.0）

**CAP Java 4.8.0 では、action の `grant` に指定した `where` 条件が実際に適用される。**

| テスト | ユーザー | 操作 | 結果 | 備考 |
|--------|---------|------|------|------|
| 3-A | alice（region=JP） | JP Order の `approve` 呼び出し | **204** ✅ | 成功（region一致） |
| 3-B | alice（region=JP） | US Order の `approve` 呼び出し | **403** ⚠️ | 拒否（region不一致） |

**当初の期待との差異**: 旧バージョンの CAP Java では action の `where` 条件はランタイムで無視されるとされていたが、**CAP Java 4.8.0 では 403 で拒否される**。すなわち、最新バージョンでは action の `where` 条件が正しく適用されるよう改善されている。

> **カスタムハンドラによる実装**: もし `where` 条件が無視されるバージョンで region 制限を実装したい場合は、`OrderServiceHandler.java` のコメントアウト部分を参照。`@Before` ハンドラで DB 照会 → ユーザー属性との照合 → 不一致なら `ctx.reject(403, ...)` の実装例を記載している。

---

### Topic 4: `CREATE` に対する `where` 条件（Java 4.0+）

**CAP Java 4.0+ では `CREATE` 時に入力データの属性チェックが動作する。**

| テスト | ユーザー | 操作 | 結果 | 期待 |
|--------|---------|------|------|------|
| 4-A | alice（region=JP） | region=JP で CREATE | **201** ✅ | 成功（region一致） |
| 4-B | alice（region=JP） | region=US で CREATE | **400** ✅ | 拒否（region不一致） |

**確認事項**: `where: (region = $user.region)` が CREATE 時の入力データに対しても適用される。region=US のデータを alice（region=JP）で登録しようとすると、`400 Bad Request: Payload contains forbidden values` が返る。

---

### Topic 5: Bound Function の権限制御

**Function（GET）も Action（POST）と同じ権限制御パターンが適用される。**

アノテーション構成:
- `READ` に `to: 'Admin5'` + `where: (region = $user.region)`
- `getSummary` に `to: 'Admin5'` + `where: (region = $user.region)`

| テスト | ユーザー | 操作 | 結果 | 意味 |
|--------|---------|------|------|------|
| 5-A | alice（Admin5ロール, region=JP） | JP Order の `getSummary` 呼び出し | **200** ✅ | ロールあり・region一致 → 成功 |
| 5-B | viewer（ロールなし） | JP Order の `getSummary` 呼び出し | **403** ✅ | ロールなし → 拒否 |
| 5-C | alice（region=JP） | US Order の `getSummary` 呼び出し | **403** ✅ | function grant の `where` が適用 |
| 5-D | alice（region=JP） | READ 一覧取得 | **JP のみ返却** ✅ | READ の `where` がフィルタとして適用 |

**切り分け検証**: `getSummary` の grant から `where` を除いた場合、alice が US Order に対して **200** が返った。つまり：

- **READ の `where` 条件は function には引き継がれない**（Action の Topic 2 と同じ挙動）
- **function grant の `where` 条件は適用される**（Action の Topic 3 と同じく 4.8.0 では有効）

---

## まとめ

| # | トピック | CAP Java 4.8.0 での動作 |
|---|---------|------------------------|
| 1 | action のデフォルト権限 | `WRITE` は action をカバーしない。action 名を `grant` に明示が必要 |
| 2 | action への `where` 非引き継ぎ | `UPDATE` の `where` は action には引き継がれない |
| 3 | action の `where` 適用 | **4.8.0 では適用される**（旧バージョンでは無視されていた） |
| 4 | `CREATE` への `where` 適用 | 入力データの属性チェックが動作する（Java 4.0+） |
| 5 | function の権限制御 | ロール制限・`where` 適用ともに Action と同様の挙動。READ の `where` は function に引き継がれない |
