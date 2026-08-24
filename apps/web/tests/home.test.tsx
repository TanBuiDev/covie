import { render, screen } from "@testing-library/react";
import { expect, it } from "vitest";
import HomePage from "../app/page";
import { BrowseLocationProvider } from "@/features/location/browse-location-provider";

it("renders the CoVie foundation shell", () => {
  render(<BrowseLocationProvider><HomePage /></BrowseLocationProvider>);
  expect(screen.getByRole("heading", { name: "CoVie" })).toBeInTheDocument();
});
