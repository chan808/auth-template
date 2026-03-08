import ProfileCard from "@/features/member/components/ProfileCard";
import ChangePasswordForm from "@/features/member/components/ChangePasswordForm";
import LogoutButton from "@/features/auth/components/LogoutButton";

export default function DashboardPage() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-6 p-8">
      <ProfileCard />
      <ChangePasswordForm />
      <LogoutButton />
    </main>
  );
}
