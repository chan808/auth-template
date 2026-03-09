"use client";

import { useTranslations } from "next-intl";
import { Button } from "@/shared/components/ui/button";

const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";

const providers = [
  { id: "google", label: "Google" },
  { id: "naver",  label: "Naver" },
  { id: "kakao",  label: "Kakao" },
] as const;

export default function SocialLoginButtons() {
  const t = useTranslations("auth.social");

  const handleLogin = (provider: string) => {
    window.location.assign(`${BACKEND_URL}/oauth2/authorization/${provider}`);
  };

  return (
    <div className="space-y-2">
      <div className="relative my-4">
        <div className="absolute inset-0 flex items-center">
          <span className="w-full border-t" />
        </div>
        <div className="relative flex justify-center text-xs uppercase">
          <span className="bg-card px-2 text-muted-foreground">{t("divider")}</span>
        </div>
      </div>
      {providers.map(({ id, label }) => (
        <Button
          key={id}
          variant="outline"
          className="w-full"
          onClick={() => handleLogin(id)}
        >
          {t("loginWith", { provider: label })}
        </Button>
      ))}
    </div>
  );
}
