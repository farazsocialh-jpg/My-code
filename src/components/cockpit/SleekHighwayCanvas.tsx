import React, { useRef, useEffect } from 'react';

interface SleekHighwayCanvasProps {
  speedKmH: number;
  rpm: number;
  gear: number;
}

export const SleekHighwayCanvas: React.FC<SleekHighwayCanvasProps> = ({
  speedKmH,
  rpm,
  gear
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animationFrameId: number;
    let offset = 0;

    const render = () => {
      const width = canvas.width;
      const height = canvas.height;

      const isStopped = speedKmH <= 0;

      // ROAD MOVEMENT LOGIC:
      // When speed is 0 -> offset DOES NOT CHANGE. The road and markers are completely stopped.
      // When speed > 0 -> movement speed increases proportionally with actual vehicle speed!
      if (!isStopped) {
        // Linear scaling with vehicle speed (e.g. 30 km/h moves slower, 120 km/h moves fast)
        const speedFactor = Math.max(0.2, speedKmH * 0.085);
        offset = (offset + speedFactor) % 100;
      }

      // Clear with dark night sky gradient
      const skyGrad = ctx.createLinearGradient(0, 0, 0, height);
      skyGrad.addColorStop(0, '#070B12');
      skyGrad.addColorStop(0.35, '#0B1320');
      skyGrad.addColorStop(0.55, '#101B2B');
      skyGrad.addColorStop(1, '#080E17');
      ctx.fillStyle = skyGrad;
      ctx.fillRect(0, 0, width, height);

      // Starfield / Ambient City Skyline in the distant horizon
      ctx.fillStyle = 'rgba(255, 255, 255, 0.4)';
      const stars = [
        { x: width * 0.15, y: height * 0.1, r: 1 },
        { x: width * 0.28, y: height * 0.06, r: 1.5 },
        { x: width * 0.45, y: height * 0.12, r: 1 },
        { x: width * 0.72, y: height * 0.08, r: 1.2 },
        { x: width * 0.88, y: height * 0.15, r: 1 },
        { x: width * 0.6, y: height * 0.05, r: 1.8 }
      ];
      stars.forEach(s => {
        ctx.beginPath();
        ctx.arc(s.x, s.y, s.r, 0, Math.PI * 2);
        ctx.fill();
      });

      // Distant horizon mountain silhouette & ambient city glow
      const horizonY = height * 0.42;

      // Horizon glow
      const horizonGlow = ctx.createRadialGradient(width / 2, horizonY, 5, width / 2, horizonY, width * 0.4);
      horizonGlow.addColorStop(0, 'rgba(77, 240, 255, 0.25)');
      horizonGlow.addColorStop(0.5, 'rgba(0, 229, 163, 0.08)');
      horizonGlow.addColorStop(1, 'transparent');
      ctx.fillStyle = horizonGlow;
      ctx.fillRect(0, horizonY - 40, width, 80);

      // Mountains / cityscape silhouette
      ctx.fillStyle = '#060A10';
      ctx.beginPath();
      ctx.moveTo(0, horizonY);
      ctx.lineTo(width * 0.15, horizonY - 14);
      ctx.lineTo(width * 0.3, horizonY - 6);
      ctx.lineTo(width * 0.42, horizonY - 18);
      ctx.lineTo(width * 0.5, horizonY - 8);
      ctx.lineTo(width * 0.62, horizonY - 20);
      ctx.lineTo(width * 0.78, horizonY - 10);
      ctx.lineTo(width * 0.9, horizonY - 16);
      ctx.lineTo(width, horizonY);
      ctx.lineTo(width, height);
      ctx.lineTo(0, height);
      ctx.closePath();
      ctx.fill();

      // Road Vanishing Perspective
      const vanishX = width / 2;
      const roadTopWidth = 14;
      const roadBottomWidth = width * 0.88;

      // Dark asphalt road bed
      ctx.beginPath();
      ctx.moveTo(vanishX - roadTopWidth / 2, horizonY);
      ctx.lineTo(vanishX + roadTopWidth / 2, horizonY);
      ctx.lineTo(vanishX + roadBottomWidth / 2, height);
      ctx.lineTo(vanishX - roadBottomWidth / 2, height);
      ctx.closePath();
      const roadGrad = ctx.createLinearGradient(0, horizonY, 0, height);
      roadGrad.addColorStop(0, '#0D1520');
      roadGrad.addColorStop(1, '#070D14');
      ctx.fillStyle = roadGrad;
      ctx.fill();

      // Road outer border glow lines (Neon Cyan)
      ctx.lineWidth = 2.5;
      ctx.strokeStyle = '#4DF0FF';
      ctx.shadowColor = '#4DF0FF';
      ctx.shadowBlur = 10;

      // Left edge
      ctx.beginPath();
      ctx.moveTo(vanishX - roadTopWidth / 2, horizonY);
      ctx.lineTo(vanishX - roadBottomWidth / 2, height);
      ctx.stroke();

      // Right edge
      ctx.beginPath();
      ctx.moveTo(vanishX + roadTopWidth / 2, horizonY);
      ctx.lineTo(vanishX + roadBottomWidth / 2, height);
      ctx.stroke();

      // Cat-eye reflective road studs along the borders
      // When stopped (speed == 0), studs stay stationary at current offset
      ctx.shadowBlur = isStopped ? 4 : 6;
      for (let i = 0; i < 14; i++) {
        const t = ((i * 12 + offset) % 100) / 100;
        const depth = Math.pow(t, 2.2);
        const y = horizonY + depth * (height - horizonY);
        const halfW = (roadTopWidth / 2) + depth * ((roadBottomWidth - roadTopWidth) / 2);
        const studSize = 1.2 + depth * 3.5;

        // Amber road lights on left & right curbs
        ctx.fillStyle = '#FFB800';
        ctx.shadowColor = '#FFB800';

        // Left stud
        ctx.beginPath();
        ctx.arc(vanishX - halfW, y, studSize, 0, Math.PI * 2);
        ctx.fill();

        // Right stud
        ctx.beginPath();
        ctx.arc(vanishX + halfW, y, studSize, 0, Math.PI * 2);
        ctx.fill();
      }

      // Moving Center Lane Dashes
      // When stopped (speed == 0), dashes remain stationary
      ctx.shadowColor = '#4DF0FF';
      ctx.shadowBlur = isStopped ? 4 : 8;
      ctx.strokeStyle = 'rgba(77, 240, 255, 0.9)';

      for (let i = 0; i < 10; i++) {
        const t1 = ((i * 16 + offset) % 100) / 100;
        const t2 = Math.min(1, t1 + 0.07);

        const d1 = Math.pow(t1, 2.4);
        const d2 = Math.pow(t2, 2.4);

        const y1 = horizonY + d1 * (height - horizonY);
        const y2 = horizonY + d2 * (height - horizonY);

        ctx.lineWidth = 1.5 + d1 * 6;
        ctx.beginPath();
        ctx.moveTo(vanishX, y1);
        ctx.lineTo(vanishX, y2);
        ctx.stroke();
      }

      // Reset shadow blur
      ctx.shadowBlur = 0;

      // Inner lane guide lines (Left & Right)
      for (const side of [-1, 1]) {
        for (let i = 0; i < 8; i++) {
          const t1 = ((i * 18 + offset) % 100) / 100;
          const t2 = Math.min(1, t1 + 0.05);
          const d1 = Math.pow(t1, 2.3);
          const d2 = Math.pow(t2, 2.3);

          const y1 = horizonY + d1 * (height - horizonY);
          const y2 = horizonY + d2 * (height - horizonY);

          const halfW1 = (roadTopWidth / 2) + d1 * ((roadBottomWidth - roadTopWidth) / 2);
          const halfW2 = (roadTopWidth / 2) + d2 * ((roadBottomWidth - roadTopWidth) / 2);

          const x1 = vanishX + side * (halfW1 * 0.52);
          const x2 = vanishX + side * (halfW2 * 0.52);

          ctx.strokeStyle = 'rgba(0, 229, 163, 0.5)';
          ctx.lineWidth = 1 + d1 * 3;
          ctx.beginPath();
          ctx.moveTo(x1, y1);
          ctx.lineTo(x2, y2);
          ctx.stroke();
        }
      }

      // Render the Kia Sportage Rear View
      // isStopped controls brake lights: ON (stationary) vs OFF (cruising)
      drawSportageRear(ctx, vanishX, height - 22, isStopped, speedKmH);

      animationFrameId = requestAnimationFrame(render);
    };

    const drawSportageRear = (
      c: CanvasRenderingContext2D, 
      cx: number, 
      cy: number, 
      isStopped: boolean,
      currentSpeed: number
    ) => {
      c.save();

      const carScale = 1.05;
      const w = 150 * carScale;
      const h = 88 * carScale;
      const topY = cy - h;

      // Ground contact shadow
      c.fillStyle = isStopped ? 'rgba(0, 0, 0, 0.85)' : 'rgba(0, 0, 0, 0.65)';
      c.beginPath();
      c.ellipse(cx, cy + 2, w * 0.56, 12, 0, 0, Math.PI * 2);
      c.fill();

      // Tire tracks / Wheels
      c.fillStyle = '#0B1118';
      c.fillRect(cx - w * 0.44, cy - 26, 18, 28);
      c.fillRect(cx + w * 0.44 - 18, cy - 26, 18, 28);

      // Car body silhouette (Modern sleek Sportage crossover contour)
      c.fillStyle = '#141E2B';
      c.strokeStyle = '#25374C';
      c.lineWidth = 1.5;

      c.beginPath();
      // Lower bumper
      c.moveTo(cx - w * 0.42, cy - 8);
      // Up to wheel arch
      c.lineTo(cx - w * 0.46, cy - 30);
      // Body shoulder crease
      c.lineTo(cx - w * 0.44, cy - 50);
      // Rear pillars tapering inwards
      c.lineTo(cx - w * 0.32, topY + 18);
      // Roofline
      c.lineTo(cx - w * 0.22, topY);
      c.lineTo(cx + w * 0.22, topY);
      // Right roof pillar
      c.lineTo(cx + w * 0.32, topY + 18);
      // Right shoulder
      c.lineTo(cx + w * 0.44, cy - 50);
      c.lineTo(cx + w * 0.46, cy - 30);
      c.lineTo(cx + w * 0.42, cy - 8);
      c.closePath();
      c.fill();
      c.stroke();

      // Rear Windshield Glass
      c.fillStyle = '#0A121A';
      c.beginPath();
      c.moveTo(cx - w * 0.30, topY + 20);
      c.lineTo(cx - w * 0.20, topY + 4);
      c.lineTo(cx + w * 0.20, topY + 4);
      c.lineTo(cx + w * 0.30, topY + 20);
      c.lineTo(cx + w * 0.34, topY + 38);
      c.lineTo(cx - w * 0.34, topY + 38);
      c.closePath();
      c.fill();

      // Glass tint reflection
      c.fillStyle = 'rgba(77, 240, 255, 0.08)';
      c.beginPath();
      c.moveTo(cx - w * 0.15, topY + 6);
      c.lineTo(cx + w * 0.05, topY + 6);
      c.lineTo(cx - w * 0.05, topY + 36);
      c.lineTo(cx - w * 0.25, topY + 36);
      c.closePath();
      c.fill();

      // Signature Connected Horizontal LED Lightbar & BRAKE LIGHTS
      const lightY = topY + 44;
      
      // When vehicle is STOPPED (speed == 0), BRAKE LIGHTS are active (intense bright red glow)
      // When vehicle is MOVING (speed > 0), running tail lightbar is active
      const brakeGlow = isStopped ? '#FF1744' : '#FF2E4C';
      const glowBlur = isStopped ? 24 : 12;

      c.shadowColor = brakeGlow;
      c.shadowBlur = glowBlur;

      // Red LED connecting strip across entire tailgate
      c.strokeStyle = isStopped ? '#FF1744' : '#FF2E4C';
      c.lineWidth = isStopped ? 4.5 : 3.0;
      c.beginPath();
      c.moveTo(cx - w * 0.41, lightY);
      c.lineTo(cx + w * 0.41, lightY);
      c.stroke();

      // Left & Right Taillight Clusters (Geometric LED blocks)
      c.fillStyle = isStopped ? '#FF1744' : '#FF4757';
      c.beginPath();
      c.roundRect(cx - w * 0.43, lightY - (isStopped ? 6 : 5), 24, isStopped ? 12 : 10, 2);
      c.roundRect(cx + w * 0.43 - 24, lightY - (isStopped ? 6 : 5), 24, isStopped ? 12 : 10, 2);
      c.fill();

      // High mount stop light on rear spoiler (Bright when stopped!)
      c.fillStyle = isStopped ? '#FF1744' : '#FF2E4C';
      c.fillRect(cx - 18, topY + 1, 36, isStopped ? 4 : 2.5);

      c.shadowBlur = 0;

      // License plate recess & plate
      c.fillStyle = '#080E14';
      c.roundRect(cx - 26, lightY + 14, 52, 16, 2);
      c.fill();
      c.fillStyle = '#EAF5FA';
      c.font = 'bold 9px monospace';
      c.textAlign = 'center';
      c.fillText('SPORTAGE', cx, lightY + 25);

      // Dual Exhaust Tips & Diffuser (AWD Trim)
      c.fillStyle = '#1A2430';
      c.fillRect(cx - 36, cy - 7, 72, 5);

      c.fillStyle = '#3A4D63';
      c.beginPath();
      c.ellipse(cx - 34, cy - 4, 6, 3, 0, 0, Math.PI * 2);
      c.ellipse(cx + 34, cy - 4, 6, 3, 0, 0, Math.PI * 2);
      c.fill();

      // When stopped, draw subtle "BRAKE / STANDSTILL" indicator glow under bumper
      if (isStopped) {
        c.fillStyle = 'rgba(255, 23, 68, 0.2)';
        c.beginPath();
        c.ellipse(cx, cy + 4, 45, 6, 0, 0, Math.PI * 2);
        c.fill();
      }

      c.restore();
    };

    render();

    return () => {
      cancelAnimationFrame(animationFrameId);
    };
  }, [speedKmH, rpm, gear]);

  return (
    <div className="w-full h-full relative overflow-hidden rounded-2xl bg-[#070B12]">
      <canvas
        ref={canvasRef}
        width={480}
        height={320}
        className="w-full h-full block"
      />
    </div>
  );
};
