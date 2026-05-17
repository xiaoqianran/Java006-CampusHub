import { routes } from '../router';

describe('router', () => {
  it('registers required page routes', () => {
    const routeNames = routes.map((route) => route.name);

    expect(routeNames).toEqual([
      'home',
      'login',
      'register',
      'resources',
      'resourceUpload',
      'resourceDetail',
      'profile'
    ]);
  });

  it('enables resource detail props', () => {
    const detailRoute = routes.find((route) => route.name === 'resourceDetail');

    expect(detailRoute?.props).toBe(true);
  });
});
