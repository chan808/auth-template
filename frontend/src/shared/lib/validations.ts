import { z } from "zod";

/**
 * 백엔드 비밀번호 검증 패턴과 동일하게 유지
 * SignupRequest / PasswordResetConfirmRequest / ChangePasswordRequest 공통 규칙:
 *   - 영문·숫자·특수문자 각 1자 이상
 *   - 출력 가능한 ASCII(0x20–0x7E)만 허용 (NIST SP 800-63B 권고)
 *   - 8자 이상, 64자 이하
 */
export const passwordSchema = z
  .string()
  .min(8, "비밀번호는 8자 이상이어야 합니다.")
  .max(64, "비밀번호는 64자 이하여야 합니다.")
  .regex(
    /^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9\s])[ -~]+$/,
    "영문, 숫자, 특수문자를 각각 1자 이상 포함해야 합니다.",
  );
