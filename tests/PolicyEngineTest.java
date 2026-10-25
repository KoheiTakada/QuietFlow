import dev.quietflow.core.*;
public class PolicyEngineTest {
    static NotificationInput n(String title, String body) { return new NotificationInput("com.example.shop", title, body, "", false); }
    public static void main(String[] args) {
        PolicyEngine p = new PolicyEngine();
        check(p.protectedContent(n("認証コード", "123456")),"otp protected");
        check(p.protectedContent(n("ログイン", "確認してください")),"login protected");
        check(p.local(n("週末セール", "クーポンあり")).level == Level.P4,"promo inference");
        check(!p.canAutoAct(n("週末セール", "割引"), new Verdict(Level.P4, 0.99, "model"), true, false),"heuristic cannot auto delete");
        check(!p.canAutoAct(n("認証コード", "123456"), new Verdict(Level.P4, 1.0, "model"), true, true),"critical always protected");
        check(!p.canAutoAct(n("お知らせ", "内容あり"), new Verdict(Level.P4, 0.97, "model"), true, true),"threshold");
        check(p.canAutoAct(n("お知らせ", "内容あり"), new Verdict(Level.P4, 0.99, "model"), true, true),"high-confidence action");
        check(!p.canAutoAct(n("お知らせ", "内容あり"), new Verdict(Level.P4, 0.99, "model"), false, true),"observe mode");
        check(!p.canAutoAct(new NotificationInput("app","sync","", "service", true), new Verdict(Level.P4,1,"model"),true,true),"ongoing protected");
        System.out.println("9 policy tests passed");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
