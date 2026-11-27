import { useEffect, useRef, useState } from "react"

/** Animates a number from its previous value to the new one. */
export default function CountUp({
  value,
  suffix = "",
  duration = 900,
}: {
  value: number
  suffix?: string
  duration?: number
}) {
  const [display, setDisplay] = useState(0)
  const current = useRef(0)

  useEffect(() => {
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches
    const length = reduce ? 1 : duration
    const begin = current.current
    const startedAt = performance.now()
    let frame = 0
    const tick = (now: number) => {
      const t = Math.min(1, (now - startedAt) / length)
      const eased = 1 - Math.pow(1 - t, 3)
      current.current = begin + (value - begin) * eased
      setDisplay(current.current)
      if (t < 1) frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [value, duration])

  return (
    <>
      {Math.round(display)}
      {suffix}
    </>
  )
}