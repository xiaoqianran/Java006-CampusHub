import { validateResourceCreate } from '../utils/resourceValidators';

describe('resource validators', () => {
  const validForm = {
    title: 'Java 笔记',
    categoryId: 1,
    fileUrl: 'https://example.com/java.pdf',
    fileSize: 0,
    fileType: 'application/pdf'
  };

  it('validates required fields and boundaries', () => {
    expect(validateResourceCreate({ ...validForm, title: '' })).toBe('资源标题不能为空');
    expect(validateResourceCreate({ ...validForm, title: 'a'.repeat(201) })).toBe(
      '资源标题最多200个字符'
    );
    expect(validateResourceCreate({ ...validForm, categoryId: 0 })).toBe('分类ID必须大于0');
    expect(validateResourceCreate({ ...validForm, fileUrl: '' })).toBe('文件地址不能为空');
    expect(validateResourceCreate({ ...validForm, fileSize: -1 })).toBe('文件大小不能为负数');
    expect(validateResourceCreate({ ...validForm, fileType: '' })).toBe('文件类型不能为空');
    expect(validateResourceCreate(validForm)).toBe('');
  });
});
