import {expect, test} from "@playwright/test";
import {resolve} from "node:path";

test.use({channel:"chrome",video:"off"});

// Isolated UI contract fixture: no login, application database reads or mutations.
for (const viewport of [{width:1280,height:720},{width:1366,height:768},{width:1536,height:864},
  {width:360,height:800},{width:390,height:844}]) {
  test(`filter panel keyboard and bounds at ${viewport.width}x${viewport.height}`, async ({page}) => {
    await page.setViewportSize(viewport);
    await page.addInitScript(() => localStorage.setItem("covie:browse-location:v1",JSON.stringify({version:1,
      location:{kind:"coordinates",lat:10.84,lng:106.81,label:"Vinhomes Grand Park"}})));
    await page.route("**/api/v1/categories",route => route.fulfill({json:[{id:"food",name:"Ăn uống"}]}));
    await page.route("**/api/v1/catalog/items?**",route => route.fulfill({json:{page:{items:[{
      id:"item-test",name:"Cơm thử giao diện",type:"PRODUCT",listingKind:"STANDARD",
      priceFrom:{amount:65000,currency:"VND"},merchant:{id:"merchant-test",name:"Bếp thử"},
      resolvedLocation:{id:"location-test",name:"Long Bình",distanceMeters:300}}],hasMore:false}}}));
    await page.goto("/search?q=cơm");
    await expect(page.getByRole("heading",{name:"Cơm thử giao diện"})).toBeVisible();
    const trigger=page.getByRole("button",{name:"Bộ lọc",exact:true});
    await trigger.click();
    const dialog=page.getByRole("dialog",{name:"Bộ lọc tìm kiếm"});
    const first=dialog.getByRole("button",{name:"Đặt lại"});
    const last=dialog.getByRole("button",{name:"Áp dụng",exact:true});
    await expect(first).toBeFocused();
    await page.keyboard.press("Shift+Tab");
    await expect(last).toBeFocused();
    await page.keyboard.press("Tab");
    await expect(first).toBeFocused();
    await dialog.getByLabel("Giá từ (₫)").fill("0");
    await dialog.getByLabel("Giá đến (₫)").fill("100000");
    await dialog.getByLabel("Giao tận nơi",{exact:true}).check();
    const bounds=await dialog.boundingBox();
    expect(bounds).not.toBeNull();
    expect(bounds!.x).toBeGreaterThanOrEqual(0);
    expect(bounds!.x+bounds!.width).toBeLessThanOrEqual(viewport.width+1);
    expect(bounds!.y+bounds!.height).toBeLessThanOrEqual(viewport.height+1);
    const applyBounds=await last.boundingBox();
    expect(applyBounds!.y).toBeGreaterThanOrEqual(bounds!.y);
    expect(applyBounds!.y+applyBounds!.height).toBeLessThanOrEqual(viewport.height);
    expect(await dialog.evaluate(element=>element.scrollWidth<=element.clientWidth)).toBeTruthy();
    await first.scrollIntoViewIfNeeded();
    await page.screenshot({path:resolve("../../.local/buyer-ui-audit",`filter-${viewport.width}x${viewport.height}.png`)});
    await page.keyboard.press("Escape");
    await expect(dialog).toHaveCount(0);
    await expect(trigger).toBeFocused();
    expect(await page.evaluate(()=>document.body.style.overflow)).not.toBe("hidden");
  });
}
