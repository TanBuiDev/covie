import { expect, type Page } from "@playwright/test";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";

export type LocalActor = {
  userId: string;
  email: string;
  password: string;
  landingPath: string;
};

export type LocalUiFixture = {
  buyerId: string;
  buyerEmail: string;
  buyerPassword: string;
  merchantId: string;
  locationId: string;
  productItemId: string;
  serviceItemId: string;
  resaleItemId: string;
  actors: {
    buyer: LocalActor;
    seller: LocalActor;
    admin: LocalActor;
  };
  scenarios: {
    abandonedCartId: string;
    completedOrderId: string;
    negotiatedOrderId: string;
    activeNeedId: string;
    restrictedNeedId: string;
    reviewId: string;
    pausedLocationId: string;
    missingCatalogItemId: string;
    currentCatalogVersion: number;
    staleCatalogVersion: number;
  };
};

export const fixturePath = path.resolve(process.cwd(), "../../.local/dev/ui-fixture.json");
export const localFixtureEnabled = process.env.COVIE_UI_FIXTURE === "1" && existsSync(fixturePath);

export function readLocalUiFixture(): LocalUiFixture {
  return JSON.parse(readFileSync(fixturePath, "utf8")) as LocalUiFixture;
}

export async function loginAs(page: Page, actor: LocalActor) {
  await page.goto("/login");
  await page.getByRole("textbox", { name: "Email" }).fill(actor.email);
  await page.getByRole("textbox", { name: "Mật khẩu" }).fill(actor.password);
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page).toHaveURL(/\/$/);
}
