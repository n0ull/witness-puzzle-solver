import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;

/** Green panels with stars, color squares and fixed polyominoes.
 * Coordinates are (column, row), top left is (0,0).
 */
class StarPuzzle {
    final int[] xs, ys;
    final int w, h;
    final int[][] stars;
    final int[][] squares;
    final Map<Point, ArrayList<Point>> shapes = new HashMap<>();
    final boolean[][] required;
    final boolean[][] horizontal, vertical;
    final boolean[][] used, wallH, wallV;
    final ArrayList<Point> path = new ArrayList<>();

    StarPuzzle(int[] xs, int[] ys) {
        this.xs = xs; this.ys = ys;
        w = xs.length - 1; h = ys.length - 1;
        stars = new int[h][w];
        squares = new int[h][w];
        required = new boolean[h + 1][w + 1];
        horizontal = new boolean[h + 1][w]; vertical = new boolean[h][w + 1];
        wallH = new boolean[h + 1][w]; wallV = new boolean[h][w + 1];
        used = new boolean[h + 1][w + 1];
    }

    static boolean green(int rgb) {
        int r = (rgb >>> 16) & 255, g = (rgb >>> 8) & 255, b = rgb & 255;
        return g > 55 && g > r * 1.6 && g > b * 1.2;
    }

    static int brightness(BufferedImage im, int x, int y) {
        int rgb = im.getRGB(x, y);
        return (((rgb >>> 16) & 255) + ((rgb >>> 8) & 255) + (rgb & 255)) / 3;
    }

    static boolean track(BufferedImage im, int x, int y) {
        return !green(im.getRGB(x, y)) && brightness(im, x, y) < 100;
    }

    static int[] peaks(int[] counts, int threshold, int offset) {
        ArrayList<Integer> result = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] < threshold) continue;
            int first = i;
            while (i + 1 < counts.length && counts[i + 1] >= threshold) i++;
            result.add(offset + (first + i) / 2);
        }
        return result.stream().mapToInt(Integer::intValue).toArray();
    }

    static boolean regular(int[] axis) {
        if (axis.length < 3 || axis.length > 9) return false;
        double step = (axis[axis.length - 1] - axis[0]) / (double)(axis.length - 1);
        for (int i = 1; i < axis.length; i++)
            if (Math.abs(axis[i] - axis[i - 1] - step) > step * .15) return false;
        return true;
    }

    static StarPuzzle detect(BufferedImage im) {
        // Locate the largest connected green panel; other green scenery is ignored.
        int iw = im.getWidth(), ih = im.getHeight();
        boolean[] seen = new boolean[iw * ih];
        int[] queue = new int[iw * ih];
        int best = 0, left = 0, right = 0, top = 0, bottom = 0;
        for (int y = 0; y < ih; y++) for (int x = 0; x < iw; x++) {
            int id = y * iw + x;
            if (seen[id] || !green(im.getRGB(x, y))) continue;
            int head = 0, tail = 1, l = x, r = x, t = y, b = y;
            queue[0] = id; seen[id] = true;
            while (head < tail) {
                int p = queue[head++], px = p % iw, py = p / iw;
                l = Math.min(l, px); r = Math.max(r, px);
                t = Math.min(t, py); b = Math.max(b, py);
                for (int[] d : Utils.delta) {
                    int nx = px + d[0], ny = py + d[1];
                    if (nx < 0 || nx >= iw || ny < 0 || ny >= ih) continue;
                    int ni = ny * iw + nx;
                    if (!seen[ni] && green(im.getRGB(nx, ny))) {
                        seen[ni] = true; queue[tail++] = ni;
                    }
                }
            }
            if (tail > best) { best = tail; left = l; right = r; top = t; bottom = b; }
        }
        if (best < 10000 || right - left < 150 || bottom - top < 150) return null;
        int[] cols = new int[right - left + 1], rows = new int[bottom - top + 1];
        for (int y = top; y <= bottom; y++) for (int x = left; x <= right; x++) {
            if (track(im, x, y)) { cols[x - left]++; rows[y - top]++; }
        }
        // The panel's shaded rim can look like an extra grid line. Grid tracks
        // sit inside the green surround, so exclude its narrow outer margin.
        int marginX = cols.length / 25, marginY = rows.length / 25;
        Arrays.fill(cols, 0, marginX, 0);
        Arrays.fill(cols, cols.length - marginX, cols.length, 0);
        Arrays.fill(rows, 0, marginY, 0);
        Arrays.fill(rows, rows.length - marginY, rows.length, 0);
        int[] xs = peaks(cols, (int)((bottom - top) * .48), left);
        int[] ys = peaks(rows, (int)((right - left) * .48), top);
        if (!regular(xs) || !regular(ys)) return null;
        StarPuzzle puzzle = new StarPuzzle(xs, ys);
        int starCount = 0, squareCount = 0;
        for (int row = 0; row < puzzle.h; row++) for (int col = 0; col < puzzle.w; col++) {
            int cx = (xs[col] + xs[col + 1]) / 2, cy = (ys[row] + ys[row + 1]) / 2;
            Color c = new Color(im.getRGB(cx, cy));
            int square = readSquare(im, xs[col], ys[row], xs[col + 1], ys[row + 1]);
            if (square > 0) { puzzle.squares[row][col] = square; squareCount++; continue; }
            // Classify geometry first. A yellow shape's center must never
            // leave behind a star clue, and large squares are not monominoes.
            ArrayList<Point> shape = readShape(im, xs[col], ys[row], xs[col + 1], ys[row + 1]);
            if (shape != null) {
                puzzle.shapes.put(new Point(col, row), shape);
            } else if (c.getRed() > 70 && c.getRed() > c.getGreen() * .65 && c.getBlue() < c.getRed() * .8) {
                puzzle.stars[row][col] = 1; starCount++;
            } else {
                if (brightness(im, cx, cy) < 40 && !green(im.getRGB(cx, cy))) {
                    puzzle.stars[row][col] = 2; starCount++;
                }
            }
        }
        if (starCount == 0 && squareCount == 0 && puzzle.shapes.isEmpty()) return null;
        int[] levels = new int[xs.length * ys.length]; int index = 0;
        for (int y : ys) for (int x : xs) levels[index++] = brightness(im, x, y);
        Arrays.sort(levels);
        double dotThreshold = levels[levels.length / 2] * .72;
        for (int row = 0; row <= puzzle.h; row++) for (int col = 0; col <= puzzle.w; col++) {
            puzzle.required[row][col] = brightness(im, xs[col], ys[row]) < dotThreshold;
            if (col < puzzle.w) puzzle.horizontal[row][col] = track(im, (xs[col] + xs[col + 1]) / 2, ys[row]);
            if (row < puzzle.h) puzzle.vertical[row][col] = track(im, xs[col], (ys[row] + ys[row + 1]) / 2);
        }
        System.out.println("Panel " + puzzle.w + "x" + puzzle.h + ": " + starCount
                + " stars, " + squareCount + " squares, " + puzzle.shapes.size() + " shapes");
        return puzzle;
    }

    static int squareColor(int rgb) {
        Color c = new Color(rgb);
        if ((c.getRed() + c.getGreen() + c.getBlue()) / 3 < 40) return 4;
        if (c.getRed() > 140 && c.getGreen() > 140 && c.getBlue() > 140) return 1;
        if (c.getRed() > 80 && c.getBlue() > 90 && c.getGreen() < c.getRed() * .65) return 3;
        if (c.getRed() > 60 && c.getRed() > c.getGreen() * .5 && c.getBlue() < c.getRed() * .95) return 2;
        return 0;
    }

    static int readSquare(BufferedImage im, int left, int top, int right, int bottom) {
        int width = right - left, height = bottom - top;
        int cx = width / 2, cy = height / 2;
        int color = squareColor(im.getRGB(left + cx, top + cy));
        if (color == 0) return 0;
        boolean[][] seen = new boolean[height][width];
        ArrayDeque<Point> pending = new ArrayDeque<>(); pending.add(new Point(cx, cy)); seen[cy][cx] = true;
        int minX = cx, maxX = cx, minY = cy, maxY = cy, count = 0;
        while (!pending.isEmpty()) {
            Point p = pending.remove(); count++;
            minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y);
            for (int[] d : Utils.delta) {
                int nx = p.x + d[0], ny = p.y + d[1];
                if (nx >= 0 && nx < width && ny >= 0 && ny < height && !seen[ny][nx]
                        && squareColor(im.getRGB(left + nx, top + ny)) == color) {
                    seen[ny][nx] = true; pending.add(new Point(nx, ny));
                }
            }
        }
        int bw = maxX - minX + 1, bh = maxY - minY + 1;
        return bw > width * .24 && bh > height * .24 && bw < width * .5 && bh < height * .5
                && Math.abs(bw - bh) < Math.max(bw, bh) * .3 && count > bw * bh * .82 ? color : 0;
    }

    /** Read separated, axis-aligned mini-squares, excluding the cell's tracks. */
    static ArrayList<Point> readShape(BufferedImage im, int left, int top, int right, int bottom) {
        int dx = (right - left) / 5, dy = (bottom - top) / 5;
        int l = left + dx, t = top + dy, width = right - left - 2 * dx, height = bottom - top - 2 * dy;
        int[] cols = new int[width], rows = new int[height];
        boolean[][] dark = new boolean[height][width];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            Color c = new Color(im.getRGB(l + x, t + y));
            boolean black = c.getRed() < 60 && c.getGreen() < 90 && brightness(im, l + x, t + y) < 45;
            boolean purple = c.getRed() > 80 && c.getBlue() > 90 && c.getGreen() < c.getRed() * .65;
            // Illumination shifts cyan mini-squares toward green. Their red
            // channel remains much higher than the surrounding panel's.
            boolean cyan = c.getRed() > 25 && c.getGreen() > 200 && c.getBlue() > c.getGreen() * .55;
            boolean yellow = squareColor(im.getRGB(l + x, t + y)) == 2;
            if (black || purple || cyan || yellow) {
                dark[y][x] = true; cols[x]++; rows[y]++;
            }
        }
        int threshold = Math.max(3, Math.min(width, height) / 15);
        // Validate the individual mini-squares. A star's solid center also
        // produces one projection peak, but its pointed outline is not square.
        boolean[][] seen = new boolean[height][width];
        for (int sy = 0; sy < height; sy++) for (int sx = 0; sx < width; sx++) {
            if (!dark[sy][sx] || seen[sy][sx]) continue;
            ArrayDeque<Point> pending = new ArrayDeque<>(); pending.add(new Point(sx, sy)); seen[sy][sx] = true;
            int count = 0, minX = sx, maxX = sx, minY = sy, maxY = sy;
            while (!pending.isEmpty()) {
                Point p = pending.remove(); count++;
                minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x);
                minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y);
                for (int[] d : Utils.delta) {
                    int nx = p.x + d[0], ny = p.y + d[1];
                    if (nx >= 0 && nx < width && ny >= 0 && ny < height && dark[ny][nx] && !seen[ny][nx]) {
                        seen[ny][nx] = true; pending.add(new Point(nx, ny));
                    }
                }
            }
            if (count < threshold * threshold) continue;
            int bw = maxX - minX + 1, bh = maxY - minY + 1;
            if (bw > (right - left) * .24 || bh > (bottom - top) * .24) return null;
            if (count < bw * bh * .80 || Math.abs(bw - bh) > Math.max(bw, bh) * .3) return null;
        }
        int[] xx = peaks(cols, threshold, 0), yy = peaks(rows, threshold, 0);
        if (xx.length == 0 || yy.length == 0 || xx.length > 4 || yy.length > 4) return null;
        ArrayList<Point> result = new ArrayList<>();
        for (int y = 0; y < yy.length; y++) for (int x = 0; x < xx.length; x++)
            if (dark[yy[y]][xx[x]]) result.add(new Point(x, y));
        if (result.isEmpty()) return null;
        // Reject disconnected marks and non-square/non-uniform arrangements.
        Set<Point> reached = new HashSet<>();
        ArrayDeque<Point> todo = new ArrayDeque<>(); todo.add(result.get(0));
        while (!todo.isEmpty()) {
            Point p = todo.remove();
            if (!reached.add(p)) continue;
            for (int[] d : Utils.delta) {
                Point q = new Point(p.x + d[0], p.y + d[1]);
                if (result.contains(q) && !reached.contains(q)) todo.add(q);
            }
        }
        return reached.size() == result.size() ? result : null;
    }

    ArrayList<Point> solve() {
        path.clear();
        for (boolean[] row : used) Arrays.fill(row, false);
        for (boolean[] row : wallH) Arrays.fill(row, false);
        for (boolean[] row : wallV) Arrays.fill(row, false);
        used[h][0] = true; path.add(new Point(0, h));
        return search(0, h) ? new ArrayList<>(path) : null;
    }

    boolean search(int x, int y) {
        if (x == w && y == 0) {
            for (int r = 0; r <= h; r++) for (int c = 0; c <= w; c++)
                if (required[r][c] && !used[r][c]) return false;
            return validRegions();
        }
        for (int[] d : Utils.delta) {
            int nx = x + d[0], ny = y + d[1];
            if (nx < 0 || nx > w || ny < 0 || ny > h || used[ny][nx]) continue;
            boolean horizontalMove = y == ny;
            int r = Math.min(y, ny), c = Math.min(x, nx);
            if (!(horizontalMove ? horizontal[r][c] : vertical[r][c])) continue;
            if (horizontalMove) wallH[r][c] = true; else wallV[r][c] = true;
            used[ny][nx] = true; path.add(new Point(nx, ny));
            if (search(nx, ny)) return true;
            path.remove(path.size() - 1); used[ny][nx] = false;
            if (horizontalMove) wallH[r][c] = false; else wallV[r][c] = false;
        }
        return false;
    }

    boolean validRegions() {
        boolean[][] seen = new boolean[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (seen[y][x]) continue;
            Map<Integer, Integer> counts = new HashMap<>();
            Set<Integer> squareColors = new HashSet<>();
            Set<Point> cells = new HashSet<>();
            ArrayList<ArrayList<Point>> pieces = new ArrayList<>();
            ArrayDeque<Point> pending = new ArrayDeque<>();
            pending.add(new Point(x, y)); seen[y][x] = true;
            while (!pending.isEmpty()) {
                Point p = pending.remove();
                cells.add(p);
                if (squares[p.y][p.x] > 0) squareColors.add(squares[p.y][p.x]);
                if (shapes.containsKey(p)) pieces.add(shapes.get(p));
                int color = stars[p.y][p.x];
                if (color > 0) counts.merge(color, 1, Integer::sum);
                for (int[] d : Utils.delta) {
                    int nx = p.x + d[0], ny = p.y + d[1];
                    if (nx < 0 || nx >= w || ny < 0 || ny >= h || seen[ny][nx]) continue;
                    boolean wall = ny == p.y ? wallV[p.y][Math.max(p.x, nx)] : wallH[Math.max(p.y, ny)][p.x];
                    if (!wall) { seen[ny][nx] = true; pending.add(new Point(nx, ny)); }
                }
            }
            for (int count : counts.values()) if (count != 2) return false;
            if (squareColors.size() > 1) return false;
            if (!pieces.isEmpty() && !canTile(cells, pieces)) return false;
        }
        return true;
    }

    static boolean canTile(Set<Point> cells, ArrayList<ArrayList<Point>> pieces) {
        int area = 0;
        for (ArrayList<Point> piece : pieces) area += piece.size();
        return area == cells.size() && tile(new HashSet<>(cells), pieces, 0);
    }

    static boolean tile(Set<Point> remaining, ArrayList<ArrayList<Point>> pieces, int index) {
        if (index == pieces.size()) return remaining.isEmpty();
        ArrayList<Point> piece = pieces.get(index);
        // Each piece may translate, but its orientation and handedness are fixed.
        Point anchor = piece.get(0);
        for (Point target : new ArrayList<>(remaining)) {
            int dx = target.x - anchor.x, dy = target.y - anchor.y;
            ArrayList<Point> placement = new ArrayList<>();
            for (Point p : piece) placement.add(new Point(p.x + dx, p.y + dy));
            if (!remaining.containsAll(placement)) continue;
            remaining.removeAll(placement);
            if (tile(remaining, pieces, index + 1)) return true;
            remaining.addAll(placement);
        }
        return false;
    }

    void draw(BufferedImage im, ArrayList<Point> solution) {
        Graphics2D g = im.createGraphics();
        g.setColor(Color.CYAN);
        g.setStroke(new BasicStroke(Math.max(5, (xs[1] - xs[0]) / 14), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 1; i < solution.size(); i++) {
            Point a = solution.get(i - 1), b = solution.get(i);
            g.drawLine(xs[a.x], ys[a.y], xs[b.x], ys[b.y]);
        }
        g.dispose();
    }
}
