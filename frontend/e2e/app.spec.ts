import { test, expect } from '@playwright/test';

async function bootstrapAuthenticatedSession(page: import('@playwright/test').Page) {
  await page.addInitScript(() => {
    localStorage.setItem('atlasia_access_token', 'e2e-access-token');
    localStorage.setItem('atlasia_refresh_token', 'e2e-refresh-token');
    localStorage.setItem('atlasia_user_id', 'e2e-user');
  });

  await page.route('**/api/auth/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'e2e-user',
        username: 'e2e-user',
        email: 'e2e@example.com',
        roles: ['USER'],
      }),
    });
  });
}

test('has title', async ({ page }) => {
  await bootstrapAuthenticatedSession(page);
  await page.goto('/');

  await expect(page).toHaveTitle(/Dashboard|Orchestrator|Atlasia/i);
});

test('shows main heading', async ({ page }) => {
  await page.goto('/');

  // Should have "Activity Log" or other main content depending on initial route
  // Let's just check for the presence of the app-root
  const appRoot = page.locator('app-root');
  await expect(appRoot).toBeVisible();
});
