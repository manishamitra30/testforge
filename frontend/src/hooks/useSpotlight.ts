import { useEffect } from "react"

/** Tracks the cursor so CSS can draw an ambient glow and a spotlight inside hovered cards. */
export function useSpotlight() {
  useEffect(() => {
    const root = document.documentElement
    let frame = 0
    const onMove = (event: MouseEvent) => {
      cancelAnimationFrame(frame)
      frame = requestAnimationFrame(() => {
        root.style.setProperty("--cx", `${event.clientX}px`)
        root.style.setProperty("--cy", `${event.clientY}px`)
        const target = event.target as Element | null
        const card = target?.closest?.('[data-slot="card"]') as HTMLElement | null
        if (card) {
          const box = card.getBoundingClientRect()
          card.style.setProperty("--mx", `${event.clientX - box.left}px`)
          card.style.setProperty("--my", `${event.clientY - box.top}px`)
        }
      })
    }
    window.addEventListener("mousemove", onMove, { passive: true })
    return () => {
      window.removeEventListener("mousemove", onMove)
      cancelAnimationFrame(frame)
    }
  }, [])
}