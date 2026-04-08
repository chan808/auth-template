"use client";

import { useQuery } from "@tanstack/react-query";
import { memberApi } from "@/features/member/api/memberApi";
import ProfileCard from "@/features/member/components/ProfileCard";
import ChangePasswordForm from "@/features/member/components/ChangePasswordForm";
import WithdrawSection from "@/features/member/components/WithdrawSection";
import LogoutButton from "@/features/auth/components/LogoutButton";

export default function DashboardPage() {
  const { data } = useQuery({
    queryKey: ["member", "me"],
    queryFn: () => memberApi.getMyInfo().then((res) => res.data.data!),
  });

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-6 p-8">
      <section className="w-full max-w-2xl rounded-xl border border-border/70 bg-muted/40 p-5 text-sm text-muted-foreground">
        <p className="font-medium text-foreground">Sample Authenticated Area</p>
        <p className="mt-2">
          This dashboard is included as a starter example for account settings,
          password change, logout, and account deletion. Replace this route with
          your real authenticated home when you start a new project.
        </p>
      </section>
      <ProfileCard />
      {/* provider가 확정된 후에만 렌더링 — 깜빡임 방지 */}
      {data !== undefined && (
        <ChangePasswordForm provider={data.provider} />
      )}
      <LogoutButton />
      <WithdrawSection />
    </main>
  );
}
