const USERNAME_PATTERN = /^[a-zA-Z0-9_]{4,20}$/;
const PHONE_PATTERN = /^1[3-9]\d{9}$/;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function validateUsername(username: string): string {
  if (!username.trim()) {
    return '用户名不能为空';
  }
  if (!USERNAME_PATTERN.test(username)) {
    return '用户名需为4-20位字母、数字或下划线';
  }
  return '';
}

export function validatePassword(password: string): string {
  if (!password) {
    return '密码不能为空';
  }
  if (password.length < 6 || password.length > 32) {
    return '密码长度需为6-32位';
  }
  return '';
}

export function validateOptionalEmail(email?: string): string {
  if (!email) {
    return '';
  }
  if (email.length > 100 || !EMAIL_PATTERN.test(email)) {
    return '邮箱格式不正确';
  }
  return '';
}

export function validateOptionalPhone(phone?: string): string {
  if (!phone) {
    return '';
  }
  if (!PHONE_PATTERN.test(phone)) {
    return '手机号格式不正确';
  }
  return '';
}
