import { render, screen } from "@testing-library/react";
import { expect, it } from "vitest";
import HomePage from "../app/page";

it("renders the CoVie foundation shell", () => {
  render(<HomePage />);
  expect(screen.getByRole("heading", { name: "CoVie" })).toBeInTheDocument();
});
