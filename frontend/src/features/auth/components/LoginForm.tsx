"use client";

import { useTranslations, useLocale } from "next-intl";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import type { AxiosError } from "axios";
import { useAuth } from "../hooks/useAuth";
import SocialLoginButtons from "./SocialLoginButtons";
import { Button } from "@/shared/components/ui/button";
import { Input } from "@/shared/components/ui/input";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/shared/components/ui/form";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/shared/components/ui/card";

const schema = z.object({
  email: z.string().email(),
  password: z.string().min(8),
});

type FormData = z.infer<typeof schema>;

export default function LoginForm() {
  const t = useTranslations("auth.login");
  const locale = useLocale();
  const searchParams = useSearchParams();
  const resetSuccess = searchParams.get("reset") === "success";
  const oauthError = searchParams.get("error");
  const { login } = useAuth();

  const form = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { email: "", password: "" },
  });

  const onSubmit = async (data: FormData) => {
    try {
      await login(data);
    } catch (error) {
      const detail = (error as AxiosError<{ detail?: string }>).response?.data
        ?.detail;
      // 미인증 계정은 백엔드 detail 메시지를 그대로 노출
      form.setError("root", { message: detail ?? t("errorMessage") });
      form.resetField("password");
    }
  };

  return (
    <Card className="w-full max-w-md">
      <CardHeader>
        <CardTitle className="text-2xl">{t("title")}</CardTitle>
      </CardHeader>
      <CardContent>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="email"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("emailLabel")}</FormLabel>
                  <FormControl>
                    <Input
                      type="email"
                      placeholder={t("emailPlaceholder")}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="password"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("passwordLabel")}</FormLabel>
                  <FormControl>
                    <Input type="password" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            {resetSuccess && (
              <p className="text-sm text-green-600">{t("resetSuccessMessage")}</p>
            )}
            {oauthError && (
              <p className="text-sm text-destructive">{decodeURIComponent(oauthError)}</p>
            )}
            {form.formState.errors.root && (
              <p className="text-sm text-destructive">
                {form.formState.errors.root.message}
              </p>
            )}
            <Button
              type="submit"
              className="w-full"
              disabled={form.formState.isSubmitting}
            >
              {t("submitButton")}
            </Button>
            <SocialLoginButtons />
            <div className="flex flex-col gap-1 text-center text-sm text-muted-foreground">
              <Link
                href={`/${locale}/signup`}
                className="hover:underline hover:text-foreground"
              >
                {t("signupLink")}
              </Link>
              <Link
                href={`/${locale}/forgot-password`}
                className="hover:underline hover:text-foreground"
              >
                {t("forgotPasswordLink")}
              </Link>
            </div>
          </form>
        </Form>
      </CardContent>
    </Card>
  );
}
