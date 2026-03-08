import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

const withNextIntl = createNextIntlPlugin("./src/i18n/request.ts");

const nextConfig: NextConfig = {
  reactCompiler: true,
  // 이메일 링크 등 외부 링크는 locale 없이 오므로 기본 locale로 redirect
  // 백엔드 app.base-url은 locale과 무관하게 유지
  async redirects() {
    return [
      {
        source: "/verify-email",
        destination: "/ko/verify-email",
        permanent: false,
      },
      {
        source: "/reset-password",
        destination: "/ko/reset-password",
        permanent: false,
      },
    ];
  },
};

export default withNextIntl(nextConfig);
