import { HttpInterceptorFn, HttpRequest, HttpHandlerFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req: HttpRequest<unknown>, next: HttpHandlerFn) => {
    const authService = inject(AuthService);
    const token = authService.getToken();

    console.log('AuthInterceptor: Injection token:', token);

    const headers: { [key: string]: string } = {};

    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    const method = req.method.toUpperCase();
    if (method === 'POST' || method === 'PUT' || method === 'DELETE' || method === 'PATCH') {
        const csrfToken = authService.getCsrfToken();
        if (csrfToken) {
            headers['X-CSRF-TOKEN'] = csrfToken;
        }
    }

    if (Object.keys(headers).length > 0) {
        const cloned = req.clone({
            setHeaders: headers
        });
        return next(cloned);
    }

    return next(req);
};
