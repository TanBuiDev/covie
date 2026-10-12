import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { SelectDropdown } from "../../components/ui/select-dropdown";

describe("SelectDropdown Component", () => {
  const options = [
    { value: "male", label: "Nam (Male)" },
    { value: "female", label: "Nữ (Female)" },
    { value: "other", label: "Khác", disabled: true },
  ];

  it("renders with placeholder and closed menu initially", () => {
    render(<SelectDropdown options={options} placeholder="Chọn giới tính" />);
    expect(screen.getByText("Chọn giới tính")).toBeInTheDocument();
    expect(screen.getByRole("combobox")).toHaveAttribute("aria-expanded", "false");
  });

  it("opens menu when clicked and closes when selecting an option", () => {
    const handleChange = vi.fn();
    render(
      <SelectDropdown
        name="gender"
        options={options}
        placeholder="Chọn giới tính"
        onChange={handleChange}
      />
    );

    const trigger = screen.getByRole("combobox");
    expect(trigger).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(trigger);

    expect(trigger).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByRole("listbox")).toBeInTheDocument();
    expect(screen.getByText("Nam (Male)")).toBeInTheDocument();
    expect(screen.getByText("Nữ (Female)")).toBeInTheDocument();

    fireEvent.click(screen.getByText("Nam (Male)"));

    expect(handleChange).toHaveBeenCalledWith("male");
    expect(trigger).toHaveAttribute("aria-expanded", "false");
    expect(screen.getByText("Nam (Male)")).toBeInTheDocument();
  });

  it("does not select disabled option", () => {
    const handleChange = vi.fn();
    render(
      <SelectDropdown
        options={options}
        onChange={handleChange}
      />
    );

    const trigger = screen.getByRole("combobox");
    fireEvent.click(trigger);
    fireEvent.click(screen.getByText("Khác"));

    expect(handleChange).not.toHaveBeenCalled();
    expect(trigger).toHaveAttribute("aria-expanded", "true");
  });

  it("supports keyboard navigation with Enter and Escape", () => {
    render(<SelectDropdown options={options} />);
    const trigger = screen.getByRole("combobox");

    fireEvent.keyDown(trigger, { key: "Enter" });
    expect(trigger).toHaveAttribute("aria-expanded", "true");

    fireEvent.keyDown(trigger, { key: "Escape" });
    expect(trigger).toHaveAttribute("aria-expanded", "false");
  });

  it("supports toHaveValue matching accessible name and value", () => {
    render(<SelectDropdown aria-label="Sắp xếp" options={options} value="male" />);
    expect(screen.getByRole("combobox", { name: "Sắp xếp" })).toHaveValue("male");
  });

  it("handles fireEvent.change for test compatibility", () => {
    const handleChange = vi.fn();
    render(<SelectDropdown aria-label="Giới tính" options={options} onChange={handleChange} />);
    fireEvent.change(screen.getByRole("combobox", { name: "Giới tính" }), { target: { value: "female" } });
    expect(handleChange).toHaveBeenCalledWith("female");
  });
});

