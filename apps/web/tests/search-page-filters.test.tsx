import {expect, it, vi} from "vitest";
import {render, screen} from "@testing-library/react";
import type {ReactNode} from "react";
import SearchPage from "@/app/(public)/search/page";
vi.mock("@/features/ads/ad-discovery-layout", () => ({ AdDiscoveryLayout: ({children}: {children: ReactNode}) => children }));
vi.mock("@/features/discovery/search-results", () => ({ SearchResults: ({initial}: {initial: unknown}) => <output data-testid="search-settings">{JSON.stringify(initial)}</output> }));

it("reads price and fulfillment filters from URLs without treating blank prices as zero", async () => {
  const page = await SearchPage({searchParams:Promise.resolve({minPrice:"0",maxPrice:"100000",
    pickup:"true",canServe:"true",shipping:"FREE"})});
  const {rerender} = render(page);
  expect(JSON.parse(screen.getByTestId("search-settings").textContent ?? "{}")).toMatchObject({minPrice:0,maxPrice:100000,pickup:true,canServe:true,shipping:"FREE"});
  const invalid = await SearchPage({searchParams:Promise.resolve({minPrice:"",maxPrice:"-1",shipping:"UNKNOWN"})});
  rerender(invalid);
  const settings = JSON.parse(screen.getByTestId("search-settings").textContent ?? "{}");
  expect(settings.minPrice).toBeUndefined();
  expect(settings.maxPrice).toBeUndefined();
  expect(settings.shipping).toBeUndefined();
});
