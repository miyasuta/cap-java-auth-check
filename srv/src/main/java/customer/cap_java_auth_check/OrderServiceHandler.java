package customer.cap_java_auth_check;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;

import cds.gen.orderservice.OrderService_;
import cds.gen.orderservice.OrdersTopic1_;
import cds.gen.orderservice.OrdersTopic1AddNoteContext;
import cds.gen.orderservice.OrdersTopic2_;
import cds.gen.orderservice.OrdersTopic2ProcessContext;
import cds.gen.orderservice.OrdersTopic3_;
import cds.gen.orderservice.OrdersTopic3ApproveContext;
import cds.gen.orderservice.OrdersTopic5_;
import cds.gen.orderservice.OrdersTopic5GetSummaryContext;

@Component
@ServiceName(OrderService_.CDS_NAME)
public class OrderServiceHandler implements EventHandler {

    private static final Logger logger = LoggerFactory.getLogger(OrderServiceHandler.class);

    @Autowired
    @Qualifier(PersistenceService.DEFAULT_NAME)
    private PersistenceService db;

    // --- Topic 1: addNote ---
    // Admin1ロールを持つユーザーのみ呼べる（grantに明示したため）
    // viewerは呼べない（403）
    @On(event = "addNote", entity = OrdersTopic1_.CDS_NAME)
    public void onAddNote(OrdersTopic1AddNoteContext ctx) {
        logger.info("addNote called with note: {}", ctx.getNote());
        ctx.setCompleted();
    }

    // --- Topic 2: process ---
    // Admin2ロールを持つユーザーが呼べる
    // whereはactionに引き継がれないため、region=USのOrderでもalice(JP)が呼べる
    @On(event = "process", entity = OrdersTopic2_.CDS_NAME)
    public void onProcess(OrdersTopic2ProcessContext ctx) {
        logger.info("process called — no region restriction (where not inherited by action)");
        ctx.setCompleted();
    }

    // --- Topic 3: approve (where条件はCAP Javaランタイムで無視される) ---
    // @restrict の where: (region = $user.region) はJavaランタイムでは適用されない
    // → region=USのOrderでもalice(JP)がapproveを呼べる
    @On(event = "approve", entity = OrdersTopic3_.CDS_NAME)
    public void onApprove(OrdersTopic3ApproveContext ctx) {
        logger.info("approve called — where condition on action grant is IGNORED by CAP Java runtime");
        ctx.setCompleted();
    }

    // --- Topic 5: getSummary function ---
    // Function は GET リクエスト。@On で結果を返す必要がある。
    @On(event = "getSummary", entity = OrdersTopic5_.CDS_NAME)
    public void onGetSummary(OrdersTopic5GetSummaryContext ctx) {
        logger.info("getSummary called");
        ctx.setResult("summary result");
        ctx.setCompleted();
    }

    // --- Topic 3 補足: カスタムハンドラでregion制限を実装する場合の例 ---
    //
    // whereが無視されるため、proper enforcement にはカスタムハンドラが必要。
    // 以下のコードをコメントアウト解除して onApprove を @Before に切り替えることで region 制限を適用できる。
    //
    // @Before(event = "approve", entity = OrdersTopic3_.CDS_NAME)
    // public void checkApproveRegion(OrdersTopic3ApproveContext ctx) {
    //     // 1. CQN（CqnSelect）からエンティティキー（UUID）を取得
    //     CqnAnalyzer analyzer = CqnAnalyzer.create(db.getModel());
    //     AnalysisResult result = analyzer.analyze(ctx.getCqn());
    //     String orderId = (String) result.targetKeys().get(OrdersTopic3_.ID);
    //
    //     // 2. DBからエンティティのregionを取得
    //     OrdersTopic3 order = db.run(Select.from(OrdersTopic3_.class).byId(orderId))
    //         .single(OrdersTopic3.class);
    //
    //     // 3. ユーザーのregion属性と照合
    //     List<String> userRegions = ctx.getUserInfo().getAttributeValues("region");
    //     if (!userRegions.contains(order.getRegion())) {
    //         ctx.reject(403, "Not authorized for this region");
    //     }
    // }
}
