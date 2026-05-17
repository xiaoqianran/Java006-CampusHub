import { AxiosHeaders, type InternalAxiosRequestConfig } from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { clearAccessToken, http, setAccessToken } from '../api/http';

describe('http client', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  afterEach(() => {
    clearAccessToken();
    vi.restoreAllMocks();
  });

  it('unwraps successful api response', async () => {
    http.defaults.adapter = async () => ({
      data: { code: 200, message: 'success', data: { id: 1 } },
      status: 200,
      statusText: 'OK',
      headers: {},
      config: {
        headers: new AxiosHeaders()
      } as InternalAxiosRequestConfig
    });

    await expect(http.get('/resource/1')).resolves.toEqual({ id: 1 });
  });

  it('injects bearer token into request headers', async () => {
    setAccessToken('access-token');
    let authorization = '';
    http.defaults.adapter = async (config) => {
      authorization = String(config.headers?.Authorization);
      return {
        data: { code: 200, message: 'success', data: null },
        status: 200,
        statusText: 'OK',
        headers: {},
        config
      };
    };

    await http.get('/resource');

    expect(authorization).toBe('Bearer access-token');
  });

  it('throws api error message', async () => {
    http.defaults.adapter = async () => {
      throw {
        isAxiosError: true,
        message: 'Request failed',
        response: {
          data: { code: 400, message: '参数错误', data: null }
        }
      };
    };

    await expect(http.get('/resource')).rejects.toThrow('参数错误');
  });
});
