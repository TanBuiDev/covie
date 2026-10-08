import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { LocationChooser, POPULAR_COMMUNITIES } from "@/features/location/location-chooser";

const mockSetLocation = vi.fn();

vi.mock("@/features/location/use-browse-location", () => ({
  useBrowseLocation: () => ({
    location: null,
    setLocation: mockSetLocation,
  }),
}));

vi.mock("@/features/profile/address-api", () => ({
  listSavedAddressesClient: vi.fn().mockResolvedValue([]),
}));

describe("LocationChooser", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders modal header, close button, and primary options", () => {
    const onCancel = vi.fn();
    render(<LocationChooser onCancel={onCancel} />);

    expect(screen.getByText("Chọn vị trí xem chợ")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Đóng" })).toBeInTheDocument();
    expect(screen.getByText("Dùng vị trí hiện tại")).toBeInTheDocument();
    expect(screen.getByText("Chọn toà nhà & Khu dân cư")).toBeInTheDocument();
  });

  it("allows selecting a popular community directly from quick pick chips", () => {
    const onComplete = vi.fn();
    render(<LocationChooser onComplete={onComplete} />);

    const chip = screen.getByRole("button", { name: /^The Rainbow/i });
    fireEvent.click(chip);

    const rainbow = POPULAR_COMMUNITIES.find((c) => c.id === "rainbow");
    expect(mockSetLocation).toHaveBeenCalledWith({
      kind: "coordinates",
      lat: rainbow?.lat,
      lng: rainbow?.lng,
      label: rainbow?.name,
    });
    expect(onComplete).toHaveBeenCalledTimes(1);
  });

  it("calls onCancel when clicking close button or backdrop", () => {
    const onCancel = vi.fn();
    render(<LocationChooser onCancel={onCancel} />);

    const closeBtn = screen.getByRole("button", { name: "Đóng" });
    fireEvent.click(closeBtn);
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it("calls onCancel when pressing Escape key", () => {
    const onCancel = vi.fn();
    render(<LocationChooser onCancel={onCancel} />);

    fireEvent.keyDown(window, { key: "Escape" });
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it("navigates to presets mode, filters by keyword, and selects community", () => {
    const onComplete = vi.fn();
    render(<LocationChooser onComplete={onComplete} />);

    // Click 'Chọn toà nhà & Khu dân cư'
    fireEvent.click(screen.getByText("Chọn toà nhà & Khu dân cư"));

    const searchInput = screen.getByRole("searchbox");
    expect(searchInput).toBeInTheDocument();

    // Type search query
    fireEvent.change(searchInput, { target: { value: "Origami" } });

    expect(screen.getByText("The Origami (Toà S6 - S10)")).toBeInTheDocument();
    expect(screen.queryByText("Vinhomes Central Park")).not.toBeInTheDocument();

    // Select the filtered community
    fireEvent.click(screen.getByRole("button", { name: /The Origami/i }));

    const origami = POPULAR_COMMUNITIES.find((c) => c.id === "origami");
    expect(mockSetLocation).toHaveBeenCalledWith({
      kind: "coordinates",
      lat: origami?.lat,
      lng: origami?.lng,
      label: origami?.name,
    });
    expect(onComplete).toHaveBeenCalledTimes(1);
  });

  it("supports manual coordinate entry mode", () => {
    const onComplete = vi.fn();
    render(<LocationChooser onComplete={onComplete} />);

    // Open presets mode then switch to manual
    fireEvent.click(screen.getByText("Chọn toà nhà & Khu dân cư"));
    fireEvent.click(screen.getByRole("button", { name: /Nhập tọa độ/i }));

    expect(screen.getByLabelText(/Vĩ độ/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Kinh độ/i)).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText(/Vĩ độ/i), { target: { value: "10.84" } });
    fireEvent.change(screen.getByLabelText(/Kinh độ/i), { target: { value: "106.81" } });
    fireEvent.change(screen.getByLabelText(/Tên khu vực/i), { target: { value: "Nhà tôi" } });

    fireEvent.click(screen.getByRole("button", { name: "Dùng vị trí này" }));

    expect(mockSetLocation).toHaveBeenCalledWith({
      kind: "coordinates",
      lat: 10.84,
      lng: 106.81,
      label: "Nhà tôi",
    });
    expect(onComplete).toHaveBeenCalledTimes(1);
  });
});
