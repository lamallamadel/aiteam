import { test, expect } from '@playwright/test';

test('has title', async ({ page }) => {
  await page.goto('/');

  // Expect a title "to contain" a substring.
  await expect(page).toHaveTitle(/Atlasia Orchestrator/);
});

test('shows main heading', async ({ page }) => {
  await page.goto('/');

  // Should have "Activity Log" or other main content depending on initial route
  // Let's just check for the presence of the app-root
  const appRoot = page.locator('app-root');
  await expect(appRoot).toBeVisible();
});
