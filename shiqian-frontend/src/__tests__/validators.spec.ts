import {
  validateOptionalEmail,
  validateOptionalAvatar,
  validateOptionalNickname,
  validateOptionalPhone,
  validatePassword,
  validateUsername
} from '../utils/validators';

describe('validators', () => {
  it('validates username boundary and pattern', () => {
    expect(validateUsername('')).toBe('用户名不能为空');
    expect(validateUsername('abc')).toBe('用户名需为4-20位字母、数字或下划线');
    expect(validateUsername('abcd')).toBe('');
    expect(validateUsername('abc-')).toBe('用户名需为4-20位字母、数字或下划线');
  });

  it('validates password boundary', () => {
    expect(validatePassword('')).toBe('密码不能为空');
    expect(validatePassword('12345')).toBe('密码长度需为6-32位');
    expect(validatePassword('123456')).toBe('');
  });

  it('validates optional contact fields', () => {
    expect(validateOptionalEmail()).toBe('');
    expect(validateOptionalEmail('user@example.com')).toBe('');
    expect(validateOptionalEmail('wrong')).toBe('邮箱格式不正确');
    expect(validateOptionalPhone()).toBe('');
    expect(validateOptionalPhone('13800138000')).toBe('');
    expect(validateOptionalPhone('12800138000')).toBe('手机号格式不正确');
  });

  it('validates profile optional fields', () => {
    expect(validateOptionalNickname('昵称')).toBe('');
    expect(validateOptionalNickname('a'.repeat(21))).toBe('昵称不能超过20个字符');
    expect(validateOptionalAvatar('https://example.com/a.png')).toBe('');
    expect(validateOptionalAvatar('a'.repeat(501))).toBe('头像URL不能超过500个字符');
  });
});
