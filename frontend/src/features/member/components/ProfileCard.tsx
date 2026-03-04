"use client";

import { useQuery } from "@tanstack/react-query";
import { memberApi } from "../api/memberApi";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/shared/components/ui/card";

export default function ProfileCard() {
  const { data, isLoading } = useQuery({
    queryKey: ["member", "me"],
    queryFn: () => memberApi.getMyInfo().then((res) => res.data.data!),
  });

  if (isLoading || !data) return null;

  return (
    <Card className="w-full max-w-md">
      <CardHeader>
        <CardTitle>내 정보</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <div>
          <p className="text-sm text-muted-foreground">이메일</p>
          <p className="font-medium">{data.email}</p>
        </div>
        <div>
          <p className="text-sm text-muted-foreground">역할</p>
          <p className="font-medium">{data.role}</p>
        </div>
      </CardContent>
    </Card>
  );
}
