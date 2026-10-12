package com.huanghy7588.xiaqiaoqiaogongjvxiang.bead;

/**
 * CIEDE2000 色差公式（Sharma et al. 2005 参考实现）。
 * 用于 Lab 色彩空间下两色之间的人眼感知色差，比 RGB 欧氏距离更接近真实观感。
 */
public final class Ciede2000 {

    private Ciede2000() { }

    public static double distance(double[] lab1, double[] lab2) {
        return distance(lab1[0], lab1[1], lab1[2], lab2[0], lab2[1], lab2[2]);
    }

    public static double distance(double L1, double a1, double b1,
                                   double L2, double a2, double b2) {
        final double kL = 1.0, kC = 1.0, kH = 1.0;

        double C1 = Math.sqrt(a1 * a1 + b1 * b1);
        double C2 = Math.sqrt(a2 * a2 + b2 * b2);
        double Cbar = (C1 + C2) / 2.0;
        double Cbar7 = Math.pow(Cbar, 7);
        double G = 0.5 * (1.0 - Math.sqrt(Cbar7 / (Cbar7 + Math.pow(25.0, 7))));

        double a1p = (1.0 + G) * a1;
        double a2p = (1.0 + G) * a2;
        double C1p = Math.sqrt(a1p * a1p + b1 * b1);
        double C2p = Math.sqrt(a2p * a2p + b2 * b2);

        double h1p = hue(a1p, b1);
        double h2p = hue(a2p, b2);

        double dLp = L2 - L1;
        double dCp = C2p - C1p;

        double dhp;
        if (C1p * C2p == 0.0) {
            dhp = 0.0;
        } else {
            double diff = h2p - h1p;
            if (diff > 180.0) diff -= 360.0;
            else if (diff < -180.0) diff += 360.0;
            dhp = diff;
        }
        double dHp = 2.0 * Math.sqrt(Math.max(0.0, C1p * C2p)) * Math.sin(Math.toRadians(dhp) / 2.0);

        double Hbarp;
        if (C1p * C2p == 0.0) {
            Hbarp = h1p + h2p;
        } else {
            double sum = h1p + h2p;
            if (Math.abs(h1p - h2p) > 180.0) {
                if (sum < 360.0) Hbarp = sum + 360.0;
                else Hbarp = sum - 360.0;
            } else {
                Hbarp = sum;
            }
            Hbarp /= 2.0;
        }

        double Cbarp = (C1p + C2p) / 2.0;
        double T = 1.0
                - 0.17 * Math.cos(Math.toRadians(Hbarp - 30.0))
                + 0.24 * Math.cos(Math.toRadians(2.0 * Hbarp))
                + 0.32 * Math.cos(Math.toRadians(3.0 * Hbarp + 6.0))
                - 0.20 * Math.cos(Math.toRadians(4.0 * Hbarp - 63.0));

        double dTheta = 30.0 * Math.exp(-Math.pow((Hbarp - 275.0) / 25.0, 2));
        double Cbarp7 = Math.pow(Cbarp, 7);
        double Rc = 2.0 * Math.sqrt(Cbarp7 / (Cbarp7 + Math.pow(25.0, 7)));

        double Lbar = (L1 + L2) / 2.0;
        double Sl = 1.0 + (0.015 * Math.pow(Lbar - 50.0, 2))
                / Math.sqrt(20.0 + Math.pow(Lbar - 50.0, 2));
        double Sc = 1.0 + 0.045 * Cbarp;
        double Sh = 1.0 + 0.015 * Cbarp * T;
        double Rt = -Math.sin(Math.toRadians(2.0 * dTheta)) * Rc;

        double termL = dLp / (kL * Sl);
        double termC = dCp / (kC * Sc);
        double termH = dHp / (kH * Sh);

        return Math.sqrt(Math.max(0.0,
                termL * termL + termC * termC + termH * termH
                        + Rt * termL * termC));
    }

    private static double hue(double a, double b) {
        if (a == 0.0 && b == 0.0) return 0.0;
        double h = Math.toDegrees(Math.atan2(b, a));
        if (h < 0.0) h += 360.0;
        return h;
    }
}
