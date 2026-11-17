package com.alisa.user.oauth;

final class OAuthAccountPage {
    private OAuthAccountPage() {}

    static final String BRAND = "<div class='brand'><span aria-hidden='true'>A</span>alisa.</div>";

    static final String STYLES = """
        *{box-sizing:border-box}
        body{margin:0;min-height:100vh;display:grid;place-items:center;background:#f5f7fc;color:#202b40;font:14px -apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC',sans-serif;padding:24px}
                    main{width:100%;max-width:440px;background:white;padding:36px;border:1px solid #e9edf3;border-radius:20px;box-shadow:0 20px 70px #2536600b}
        .brand{display:flex;gap:10px;align-items:center;font-size:29px;font-weight:750;letter-spacing:-1px}
        .brand span{display:grid;place-items:center;background:#5267e8;color:white;width:38px;height:40px;border-radius:11px;font-size:22px}
        .eyebrow{font-size:11px;color:#5267e8;font-weight:600;margin:30px 0 8px}
        h1{font-size:25px;margin:0 0 10px;letter-spacing:-.5px}
        .intro{font-size:12px;color:#8a93a4;line-height:1.8;margin:0 0 27px}
        @media(max-width:480px){main{padding:28px 24px}}
        """;
}
