using { com.example as db } from '../db/schema';

service OrderService @(requires: 'authenticated-user') {

  // --- Topic 1: WRITEはactionをカバーしない ---
  // Admin1はWRITEロールを持つが、addNoteはgrantに明示しないと呼べない
  @(restrict: [
    { grant: 'READ' },
    { grant: 'WRITE',   to: 'Admin1' },
    { grant: 'addNote', to: 'Admin1' }
  ])
  entity OrdersTopic1 as projection on db.Orders
  actions {
    action addNote(note: String);
  }

  // --- Topic 2: UPDATEのwhere条件はactionに引き継がれない ---
  // Admin2はregion=JP のOrderしかUPDATEできないが、processアクションにはwhere制限がない
  @(restrict: [
    { grant: 'READ' },
    { grant: ['CREATE', 'DELETE'], to: 'Admin2' },
    { grant: 'UPDATE',  to: 'Admin2', where: (region = $user.region) },
    { grant: 'process', to: 'Admin2' }
  ])
  entity OrdersTopic2 as projection on db.Orders
  actions {
    action process();
  }

  // --- Topic 3: Javaではaction grantのwhereが無視される ---
  // whereを指定してもCAP Javaランタイムはactionのwhere条件を適用しない
  @(restrict: [
    { grant: 'READ' },
    { grant: ['CREATE', 'DELETE'], to: 'Admin3' },
    { grant: 'approve', to: 'Admin3', where: (region = $user.region) }
  ])
  entity OrdersTopic3 as projection on db.Orders
  actions {
    action approve();
  }

  // --- Topic 4: Java 4.0+: CREATEのwhereで入力データを検証 ---
  // region = $user.region を満たさないCREATEリクエストは400で拒否される
  @(restrict: [
    { grant: 'READ' },
    { grant: ['CREATE', 'UPDATE', 'DELETE'], to: 'Admin4',
      where: (region = $user.region) }
  ])
  entity OrdersTopic4 as projection on db.Orders;

  // --- Topic 5: Bound Function の権限制御 ---
  // 検証1: viewerはfunctionを呼べない（ロール制限）
  // 検証2: READのwhere条件はfunctionに引き継がれるか
  // 検証3: functionのgrantにwhereを指定した場合、適用されるか（Topic3と同じ問い）
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
