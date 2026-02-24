export function renderSparklineSVG(values: number[], opts?: { width?: number; height?: number; stroke?: string; strokeWidth?: number }) {
    const width = opts?.width ?? 120;
    const height = opts?.height ?? 36;
    const stroke = opts?.stroke ?? "#1f77b4";
    const strokeWidth = opts?.strokeWidth ?? 2;

    if (!values.length) return { width, height, pathD: "", stroke, strokeWidth };

    const minV = Math.min(...values);
    const maxV = Math.max(...values);
    const range = maxV - minV || 1;

    const points = values.map((v, i) => {
        const x = (i / (values.length - 1 || 1)) * (width - 2) + 1;
        const y = height - 1 - ((v - minV) / range) * (height - 2);
        return { x, y };
    });

    const pathD = points
        .map((p, idx) => (idx === 0 ? `M ${p.x.toFixed(2)} ${p.y.toFixed(2)}` : `L ${p.x.toFixed(2)} ${p.y.toFixed(2)}`))
        .join(" ");

    return { width, height, pathD, stroke, strokeWidth };
}