type AuthPage = "login" | "signup" | "forgot-password";

type SearchParamsLike = {
  toString(): string;
};

interface AuthPageHrefOptions {
  locale: string;
  page: AuthPage;
  returnTo?: string | null;
  email?: string | null;
  error?: string | null;
  reset?: string | null;
}

export function normalizeReturnTo(value?: string | null): string | null {
  if (!value) return null;
  return value.startsWith("/") && !value.startsWith("//") ? value : null;
}

export function resolvePostLoginPath(locale: string, returnTo?: string | null): string {
  return normalizeReturnTo(returnTo) ?? `/${locale}/dashboard`;
}

export function buildCurrentPath(
  pathname: string,
  searchParams?: SearchParamsLike | null,
): string {
  const query = searchParams?.toString();
  return query ? `${pathname}?${query}` : pathname;
}

export function buildAuthPageHref({
  locale,
  page,
  returnTo,
  email,
  error,
  reset,
}: AuthPageHrefOptions): string {
  const params = new URLSearchParams();
  const normalizedReturnTo = normalizeReturnTo(returnTo);
  const normalizedEmail = email?.trim();

  if (normalizedReturnTo) {
    params.set("returnTo", normalizedReturnTo);
  }

  if (normalizedEmail) {
    params.set("email", normalizedEmail);
  }

  if (error) {
    params.set("error", error);
  }

  if (reset) {
    params.set("reset", reset);
  }

  const query = params.toString();
  return query ? `/${locale}/${page}?${query}` : `/${locale}/${page}`;
}
