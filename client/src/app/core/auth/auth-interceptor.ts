import { HttpInterceptorFn } from '@angular/common/http';

const XSRF_COOKIE_NAME = 'XSRF-TOKEN';
const XSRF_HEADER_NAME = 'X-XSRF-TOKEN';
const MUTATING_METHODS = ['POST', 'PUT', 'PATCH', 'DELETE'];

function getCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
  return match ? decodeURIComponent(match[1]) : null;
}

/**
 * Angular's built-in XSRF interceptor only attaches the token to relative URLs,
 * but this app calls an absolute API URL, so the token is added manually here.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!MUTATING_METHODS.includes(req.method)) {
    return next(req);
  }

  const token = getCookie(XSRF_COOKIE_NAME);
  if (!token) {
    return next(req);
  }

  return next(req.clone({ setHeaders: { [XSRF_HEADER_NAME]: token } }));
};
