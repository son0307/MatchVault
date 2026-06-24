import { useEffect, useId, useRef, useState } from "react";
import { CircleHelp } from "lucide-react";

export function RankingInfoButton({ description }: { description: string }) {
  const [isOpen, setIsOpen] = useState(false);
  const popoverId = useId();
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function closeOnOutsideClick(event: MouseEvent) {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }

    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setIsOpen(false);
      }
    }

    document.addEventListener("mousedown", closeOnOutsideClick);
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("mousedown", closeOnOutsideClick);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, []);

  return (
    <div className="ranking-info" ref={rootRef}>
      <button
        aria-controls={popoverId}
        aria-describedby={isOpen ? popoverId : undefined}
        aria-expanded={isOpen}
        aria-label="정렬 기준 보기"
        className="ranking-info-button"
        onClick={() => setIsOpen((current) => !current)}
        type="button"
      >
        <CircleHelp aria-hidden="true" size={19} />
      </button>
      {isOpen ? (
        <div className="ranking-info-popover" id={popoverId} role="tooltip">
          {description}
        </div>
      ) : null}
    </div>
  );
}
