"""Original low-poly RU3-style exterior, in metres. +X points toward the grille."""
import math
import struct
from bisect import bisect_right


def rgba(hexcode):
    return tuple(bytes.fromhex(hexcode)) + (255,)


def material(color, gloss=.50, metal=0, lamp=0, text=False):
    return (rgba(color), (int(gloss * 255), int(metal * 255), int(lamp * 255), 255 if text else 0))


PAINT = material('591925', .97, .87)
PAINT_EDGE = material('65212f', .96, .87)
SEAM = material('2d1820', .77, .52)
PANEL = material('25242a', .50, .16)
CLADDING = material('202126', .36)
EDGE_TRIM = material('505052', .72, .37)
RIM_SATIN = material('393940', .73, .54)
TRIM = material('131319', .45)
RUBBER = material('15151a', .14)
TYRE = material('252529', .27)
GLASS = material('0b0e13', .98, .03)
GLASS_RIM = material('111218', .59)
PILLAR_SHINE = material('333238', .75, .15)
CHROME = material('9a9ca0', .92, .65)
DARK_ALLOY = material('494851', .74, .52)
POLISH = material('c0c3c4', .98, .70)
DARK_HUB = material('25242a', .49, .28)
FRONT_LIGHT = material('929a9f', .89, .21, .41)
FRONT_GLASS = material('5e626b', .96, .16)
REFLECTOR = material('b2b7bd', .98, .60)
PROJECTOR = material('a7b9c5', .98, .32, .56)
FASCIA = material('201c23', .89, .19)
REAR_LIGHT = material('a52838', .91, .11, 1)
REAR_EDGE = material('b54450', .85, .15, 0.85)
REAR_GLASS = material('771e28', .89, .15)
CLEAR = material('899aa7', .90)
FOG = material('6d727b', .89, .24, .68)
DECAL = material('ffffff', text=True)
FLOOR = ((0, 0, 0, 0), (0, 0, 0, 0))

# Bounding boxes are in atlas pixels, inset on upload to avoid neighbouring mip texels.
ATLAS = {
    'front': (8, 8, 496, 100), 'rear': (8, 120, 298, 195),
    'honda': (332, 126, 162, 145), 'vezel': (14, 340, 203, 50),
    'hybrid': (241, 339, 238, 40), 'rs': (260, 401, 139, 56),
    'plaque': (47, 405, 56, 87),
}


def plus(a, b): return tuple(x + y for x, y in zip(a, b))
def minus(a, b): return tuple(x - y for x, y in zip(a, b))
def mul(a, k): return tuple(x * k for x in a)
def cross(a, b): return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
def norm(a):
    length = math.sqrt(sum(x*x for x in a))
    return mul(a, 1 / length) if length > 1e-9 else (0, 1, 0)
def mix(a, b, t): return a + (b - a) * t
def lin(a, b, n): return [mix(a, b, i / n) for i in range(n + 1)]


class Mesh:
    def __init__(self):
        self.vertices = []
        self.indices = []

    def vert(self, p, n, mat, uv=(0., 0.)):
        i = len(self.vertices)
        n = norm(n)
        self.vertices.append(struct.pack('<3f4b4B2H4B', *p,
                             *(round(max(-1, min(1, v)) * 127) for v in n), 0,
                             *mat[0], *(round(min(1, max(0, t)) * 65535) for t in uv), *mat[1]))
        return i

    def tri(self, a, b, c):
        self.indices.extend((a, b, c))

    def poly(self, points, mat, normal=None, uv=None):
        normal = normal or cross(minus(points[1], points[0]), minus(points[2], points[0]))
        ids = [self.vert(p, normal, mat, uv[i] if uv else (0., 0.)) for i, p in enumerate(points)]
        for i in range(2, len(ids)):
            self.tri(ids[0], ids[i - 1], ids[i])

    def patch(self, pos, columns, rows, mat, reverse=False, normal=None):
        first, cols = len(self.vertices), len(columns)
        for v in rows:
            for u in columns:
                n = normal
                if n is None:
                    n = cross(minus(pos(u + .0001, v), pos(u - .0001, v)),
                              minus(pos(u, v + .0001), pos(u, v - .0001)))
                    if reverse: n = mul(n, -1)
                self.vert(pos(u, v), n, mat)
        for j in range(len(rows) - 1):
            for i in range(cols - 1):
                a = first + j * cols + i
                self.tri(a, a + 1, a + cols + 1)
                self.tri(a, a + cols + 1, a + cols)

    def ribbon(self, pts, width, mat, normal):
        for i in range(len(pts) - 1):
            a, b = pts[i:i+2]
            shift = mul(norm(cross(normal, minus(b, a))), width * .5)
            self.poly([minus(a, shift), minus(b, shift), plus(b, shift), plus(a, shift)], mat, normal)

    def ellipsoid(self, c, extent, mat, n=16, rings=8):
        def points(u, v):
            p = (math.cos(v) * math.cos(u), math.sin(v), math.cos(v) * math.sin(u))
            return plus(c, tuple(e * w for e, w in zip(extent, p)))
        self.patch(points, lin(0, math.tau, n), lin(-math.pi/2 + .0001, math.pi/2 - .0001, rings), mat, reverse=True)

    def atlas(self, corners, name, normal):
        x, y, w, h = ATLAS[name]
        l, r = (x + 1) / 512, (x + w - 1) / 512
        t, b = (y + 1) / 512, (y + h - 1) / 512
        self.poly(corners, DECAL, normal, [(l, t), (r, t), (r, b), (l, b)])

    def save(self, path):
        # Merge only byte-identical vertices so seams, normals and decals stay intact.
        unique = {}
        remap = [unique.setdefault(vertex, len(unique)) for vertex in self.vertices]
        assert len(unique) <= 65535, 'The head unit only needs 16-bit indices'
        with open(path, 'wb') as out:
            out.write(struct.pack('<4sIII', b'VZ3D', 1, len(unique), len(self.indices)))
            out.write(b''.join(unique))
            out.write(struct.pack(f'<{len(self.indices)}H', *(remap[i] for i in self.indices)))
        print(f'{len(unique):,} vertices / {len(self.indices) // 3:,} triangles ({path.stat().st_size // 1024} KiB raw)')


# x, widest half-width at the shoulder, fender / hood deck height, skirt height
BODY = [(-2.125, .746, 1.084, .328), (-2.05, .817, 1.107, .267), (-1.91, .868, 1.114, .232),
        (-1.67, .882, 1.107, .236), (-1.31, .881, 1.078, .238), (-.95, .869, 1.057, .229),
        (-.48, .860, 1.044, .218), (.12, .861, 1.042, .217), (.61, .868, 1.034, .228),
        (1.02, .873, 1.014, .236), (1.32, .886, .990, .238), (1.57, .877, .959, .245),
        (1.83, .853, .934, .250), (2.03, .825, .894, .272), (2.121, .761, .852, .309)]
# x, roof spine, roof half-width, sill height, sill half-width
CABIN = [(-1.965, 1.141, .686, 1.105, .716), (-1.87, 1.281, .661, 1.105, .759),
         (-1.69, 1.431, .606, 1.091, .762), (-1.40, 1.540, .583, 1.070, .750),
         (-1.13, 1.594, .572, 1.057, .737), (-.76, 1.626, .565, 1.048, .734),
         (-.36, 1.624, .562, 1.044, .737), (.01, 1.590, .563, 1.038, .743),
         (.315, 1.500, .577, 1.031, .754), (.517, 1.403, .603, 1.025, .765),
         (.741, 1.261, .666, 1.016, .778), (1.098, 1.045, .748, 1.008, .755)]
WHEEL_X = (-1.305, 1.313)
WHEEL_Y = .345
ARCH = .394


def spring(rows, x):
    xs = [r[0] for r in rows]
    j = max(0, min(len(rows)-2, bisect_right(xs, x)-1))
    a, b = rows[j:j+2]
    p, q = rows[max(0, j-1)], rows[min(len(rows)-1, j+2)]
    t = max(0., min(1., (x - a[0]) / (b[0]-a[0])))
    h00, h10 = 2*t**3 - 3*t*t + 1, t**3 - 2*t*t + t
    h01, h11 = -2*t**3 + 3*t*t, t**3 - t*t
    w = b[0] - a[0]
    return tuple(h00 * a[i] + h01 * b[i] + w * (h10 * (b[i]-p[i]) / (b[0]-p[0]) + h11 * (q[i]-a[i]) / (q[0]-a[0]))
                 for i in range(1, len(a)))


def shoulder_line(x):
    return .842 - .112*x + .012*x*x


def flank(x, y, sign):
    width, deck_y, _ = spring(BODY, x)
    belt = (y-.21) / max(.48, deck_y-.21)
    tuck = .041 * max(0, (belt-.76)/.24)**1.25
    rocker = .078 * max(0, (.44-belt)/.44)**1.54
    def fade(t):
        t = min(1, max(0, t))
        return t*t*(3-2*t)
    door = fade((x+1.89)/.47) * fade((1.29-x)/.44)
    crease = y - shoulder_line(x)
    # A crisp falling shoulder over a recessed door, matching the reference side profile.
    sculpt = door*(.021*math.exp(-(crease/.059)**2) - .050*math.exp(-((crease+.132)/.158)**2))
    sculpt += door*.014*math.exp(-((y-(.341-.023*x))/.083)**2)
    nose = fade((x-1.57)/.46)
    sculpt += nose*(.032*math.exp(-((y-(.683-.060*(x-1.75)))/.064)**2) - .031*math.exp(-((y-.555)/.11)**2))
    r = min(math.hypot(x-center, y-WHEEL_Y) for center in WHEEL_X)
    fender = .021*math.exp(-((r-(ARCH+.070))/.123)**2)
    return x, y, sign * (width - tuck - rocker + sculpt + fender)


def body(x, v, sign):
    _, top, skirt = spring(BODY, x)
    dx = min(abs(x - c) for c in WHEEL_X)
    if dx < ARCH:
        skirt = max(skirt, WHEEL_Y + math.sqrt(max(0, ARCH*ARCH - dx*dx)))
    return flank(x, mix(skirt, top, v), sign)


def deck(x, u):
    _, y, w = body(x, 1, 1)
    hood = math.exp(-((x-1.60)/.415)**2)
    crown = (.029 + .044*hood) * (1-u*u)
    crown += .006*hood*math.exp(-((abs(u)-.73)/.16)**2)
    return x, y + crown, w * u


def cabin(x, v, sign):
    y, roof, sill, base = spring(CABIN, x)
    return x, mix(sill, y-.045, v), sign * mix(base, roof, v)


def roof(x, u):
    y, w, _, _ = spring(CABIN, x)
    return x, y - .045*u*u, w * u


def face(sign, z, y, offset=0):
    rise = -.63*max(.43-y,0) - .25*max(y-.744,0) if sign > 0 else 0
    return (2.120 if sign > 0 else -2.123) + sign*(.067*(1-(z/.83)**2) + rise + offset), y, z


def decorate(m, sign, silhouette, mat, offset):
    # Keep trim flush with the curved bumper; a single large triangle would sink into it.
    def clip(points, edge, direction, axis=0):
        result = []
        if not points:
            return result
        for a, b in zip(points[-1:] + points[:-1], points):
            starts_inside = direction*(a[axis]-edge) >= 0
            ends_inside = direction*(b[axis]-edge) >= 0
            if starts_inside != ends_inside:
                t = (edge-a[axis])/(b[axis]-a[axis])
                result.append((mix(a[0],b[0],t),mix(a[1],b[1],t)))
            if ends_inside:
                result.append(b)
        return result
    l, r = min(z for z, _ in silhouette), max(z for z, _ in silhouette)
    columns = lin(l, r, max(1, math.ceil((r - l)/.127)))
    for a, b in zip(columns, columns[1:]):
        strip = clip(clip(silhouette, a, 1), b, -1)
        bands = [strip] if sign < 0 else [clip(strip,.43,-1,1),clip(clip(strip,.43,1,1),.744,-1,1),clip(strip,.744,1,1)]
        for band in bands:
            if len(band) >= 3:
                mid_y = sum(y for _,y in band)/len(band)
                roll = (-.37 if mid_y < .43 else .11 if mid_y > .744 else .04) if sign > 0 else .08
                m.poly([face(sign,z,y,offset) for z,y in band],mat,(sign,roll,(a+b)*.105))


def decal(m, sign, z, y, w, h, name, stand):
    # On either end the left edge of the image has the correct orientation.
    corners = [(z + sign*w*.5, y+h*.5), (z - sign*w*.5, y+h*.5),
               (z - sign*w*.5, y-h*.5), (z + sign*w*.5, y-h*.5)]
    # Flat and slightly forward of the curved bumper or hatch.
    x = face(sign, z, y, stand)[0]
    m.atlas([(x, aY, aZ) for aZ, aY in corners], name, (sign, 0, 0))


def skin(m):
    # More samples around each wheel opening prevent angular arch edges.
    xs = set(lin(BODY[0][0], BODY[-1][0], 68))
    for x in WHEEL_X:
        xs.update(x + ARCH * math.sin(math.pi*i/24) for i in range(-12, 13))
        xs.update((x-ARCH-.001, x+ARCH+.001))
    xs = sorted(xs)
    for side in (-1, 1):
        # Start from the smooth body and bring one row of polygons through the shoulder crease.
        rows = [0, .09, .20, .33, .45, .56, .66, .76, .86, .94, 1]
        m.patch(lambda x, v: body(x, v, side), xs, rows, PAINT, side < 0)
        m.patch(lambda x, v: plus(body(x, v, side), (0, -.002, side*.007)), xs, [0, .092], CLADDING, side < 0)
        for center in WHEEL_X:
            def arch(u, t):
                radius = ARCH + .046*t
                x = center + radius * math.sin(u)
                y = max(.234, WHEEL_Y + radius * math.cos(u))
                return plus(flank(x, y, side), (0, 0, side*(.011 + .011*math.sin(t*math.pi))))
            angles = lin(-1.86, 1.86, 28)
            m.patch(arch, angles, [0,.18,.78,1], CLADDING, side < 0, normal=(0,.15,side))
            m.ribbon([arch(u,.92) for u in angles], .0075, EDGE_TRIM, (0,.18,side))
        # Door shut lines follow the curved flanks instead of floating over the side.
        for line in [[(-.39,.282),(-.34,.48),(-.31,.72),(-.29,1.039)],
                     [(.782,.272),(.825,.460),(.860,.74),(.852,1.014)],
                     [(-1.03,.327),(-1.018,.58),(-1.208,.843),(-1.438,1.064)]]:
            pts = []
            for (aa, va), (bb, vb) in zip(line, line[1:]):
                pts.extend(plus(flank(mix(aa,bb,t), mix(va,vb,t), side), (0,0,side*.008)) for t in lin(0,1,7)[:-1])
            pts.append(plus(flank(*line[-1], side), (0,0,side*.009)))
            m.ribbon(pts, .0055, SEAM, (0, 0, side))
        # Lower rocker moulding and its thin bright blade.
        for y, size, trim in [(.256,.053,CLADDING),(.277,.009,EDGE_TRIM)]:
            m.ribbon([plus(flank(x,y,side), (0,0,side*.011)) for x in lin(-.835,.837,22)], size, trim, (0,.18,side))
        x,y,z = flank(-.139,.961,side)
        m.ellipsoid((x,y,z+side*.005), (.107,.029,.012), SEAM, 14, 6)
        m.ellipsoid((x+.006,y+.004,z+side*.016), (.096,.017,.013), CHROME, 16, 6)
        # The rear handle is tucked into the black upper quarter trim.
        m.ribbon([plus(cabin(x,f,side),(0,0,side*.012)) for x,f in [(-1.406,.22),(-1.372,.42),(-1.350,.56)]],
                 .039, GLASS_RIM, (0,.12,side))
        m.ribbon([plus(cabin(x,f,side),(0,0,side*.015)) for x,f in [(-1.385,.31),(-1.362,.47)]],
                 .014, RIM_SATIN, (0,.12,side))
        # Small Hybrid finisher in the front fender, outside the main badge/plate set.
        corners = [(x,y) for x,y in [(1.003-side*.065,.883),(1.003+side*.065,.883),
                                     (1.003+side*.065,.861),(1.003-side*.065,.861)]]
        m.atlas([plus(flank(x,y,side),(0,0,side*.014)) for x,y in corners], 'hybrid', (0,.09,side))
    m.patch(deck, lin(BODY[0][0], BODY[-1][0], 68), lin(-1, 1, 10), PAINT, reverse=True)
    for side in (-1, 1):
        hood_seam = [plus(deck(x, side*mix(.947, .795, t)), (0, .005, 0))
                     for x, t in zip(lin(1.045, 2.102, 18), lin(0, 1, 18))]
        m.ribbon(hood_seam, .008, SEAM, (0, 1, 0))
    # The lower pan bridges over the inboard part of the wheel wells.
    m.poly([(-2.06,.245,-.66),(2.09,.286,-.66),(2.09,.286,.66),(-2.06,.245,.66)], TRIM, (0,-1,0))
    # Joining caps at the front and rear, kept sculpted near the corners.
    for sign, row in [(-1, BODY[0]), (1, BODY[-1])]:
        _, _, high, base = row
        def nose(u, v):
            zMax = abs(body(row[0], v, 1)[2])
            return face(sign, u*zMax, mix(base, high, v))
        corner_rows = sorted(set(lin(0,1,12) + ([(y-base)/(high-base) for y in (.43,.744)] if sign > 0 else [])))
        m.patch(nose, lin(-1, 1, 24), corner_rows, PAINT, normal=(sign, .14, 0))
        m.patch(lambda u, v: (mix(row[0], face(sign, deck(row[0], u)[2], high, 0)[0], v),
                              mix(deck(row[0], u)[1], high, v), deck(row[0], u)[2]),
                lin(-1, 1, 20), [0, 1], PAINT, normal=(sign*.4, 1, 0))
        for side in (-1, 1):
            m.patch(lambda u,v: (mix(row[0], nose(side,v)[0],u), *body(row[0],v,side)[1:]),
                    [0, 1], corner_rows, PAINT, normal=(sign*.56,.12,side*.85))


def greenhouse(m):
    m.patch(roof, lin(CABIN[0][0], CABIN[-1][0], 48), lin(-1, 1, 12), PAINT, reverse=True)
    for lower, upper in [(-1.941, -1.422), (.225, 1.066)]:
        m.patch(lambda x, t: plus(roof(x, t*.942), (0, .006, 0)), lin(lower, upper, 13), lin(-1, 1, 11), GLASS_RIM, reverse=True)
        m.patch(lambda x, t: plus(roof(x, t*.892), (0, .009, 0)), lin(lower+.023, upper-.024, 13), lin(-1, 1, 11), GLASS, reverse=True)
    for side in (-1, 1):
        m.patch(lambda x, v: cabin(x, v, side), lin(CABIN[0][0], CABIN[-1][0], 50), lin(0, 1, 5), PAINT, side < 0)
        def window(x,v,inset=0):
            lower, upper = spring([(-1.855,.48,.53),(-1.721,.294,.662),(-1.496,.130,.865),
                                   (-1.230,.081,.946),(-.592,.068,.949),(.053,.061,.948),
                                   (.513,.091,.918),(.755,.149,.898),(.931,.355,.752),(.980,.51,.556)],x)
            return plus(cabin(x,mix(lower+inset,upper-inset,v),side), (0,.002,side*(.006+.05*inset)))
        m.patch(window,lin(-1.855,.980,45),[0,1],GLASS_RIM,side<0)
        for a,b in [(-1.822,-1.518),(-1.485,-.402),(-.31,.963)]:
            m.patch(lambda x,v:window(x,v,.041),lin(a,b,21),[0,.5,1],GLASS,side<0)
        for pillar in (-1.505,-.370):
            m.ribbon([plus(window(pillar,v,.017),(0,0,side*.010)) for v in lin(0,1,5)],.013,PILLAR_SHINE,(0,.09,side))
            m.ribbon([plus(window(pillar+.020,v,.022),(0,0,side*.009)) for v in lin(0,1,4)],.007,GLASS_RIM,(0,.09,side))
        m.ribbon([plus(window(x,0,.014),(0,0,side*.003)) for x in lin(-1.821,.949,30)], .009, EDGE_TRIM, (0,.30,side))
        m.ribbon([plus(window(x,1,.002),(0,.003,side*.003)) for x in lin(-1.821,.949,30)], .008, PAINT_EDGE, (0,.40,side))
        # Small triangular mount and two-tone mirror housing.
        m.poly([(.783,1.119,side*.757),(.633,1.179,side*.789),(.511,1.082,side*.809),(.773,1.038,side*.798)],
               GLASS_RIM,(.2,-.03,side))
        m.ellipsoid((.596,1.109,side*.823),(.090,.033,.096),TRIM,12,5)
        m.ellipsoid((.582,1.159,side*.913),(.144,.077,.101),PAINT_EDGE,18,8)
        m.ellipsoid((.514,1.137,side*.928),(.078,.048,.082),GLASS_RIM,12,6)
        m.ribbon([(.696,1.151,side*.906),(.659,1.150,side*.985),(.598,1.146,side*1.011)],.008,CLEAR,(.41,.04,side))
    # Dark rear-window wiper and lip spoiler with a discreet brake strip.
    m.ribbon([plus(roof(x, u), (0, .013, 0)) for x, u in [(-1.918,-.33),(-1.902,-.18),(-1.881,.08),(-1.872,.46)]], .026, TRIM, (-.67,.7,0))
    def spoiler(u, v): return (-1.585 + .148*v + .060*u*u, 1.542 - .027*u*u + .016*v, .588*u)
    m.patch(spoiler, lin(-1,1,20), lin(0,1,3), PAINT_EDGE, normal=(-.32,.95,0))
    m.ribbon([spoiler(u, -.01) for u in lin(-.70,.70,12)], .017, REAR_GLASS, (-1,.12,0))


def tyres(m):
    for x in WHEEL_X:
        for side in (-1, 1):
            N = 36
            cols = lin(0, math.tau, N)[:-1]
            def ring(profile, mat):
                origin = len(m.vertices)
                for j, (z, r) in enumerate(profile):
                    a, b = profile[max(j-1, 0)], profile[min(j+1, len(profile)-1)]
                    dr, dz = b[1] - a[1], b[0] - a[0]
                    for angle in cols:
                        c, s = math.cos(angle), math.sin(angle)
                        m.vert((x+r*c, WHEEL_Y+r*s, (.744+z)*side), (c*dz, s*dz, -dr*side), mat)
                for j in range(len(profile)-1):
                    for i in range(N):
                        a = origin+j*N+i
                        b = origin+j*N+((i+1)%N)
                        m.tri(a,b,b+N); m.tri(a,b+N,a+N)
            def disc(r, z, mat):
                m.poly([(x+r*math.cos(t), WHEEL_Y+r*math.sin(t), side*(.744+z)) for t in cols], mat, (0, 0, side))
            ring([(-.106,.248),(-.091,.313),(-.066,.339),(-.042,.345),(.053,.345),(.078,.340),(.101,.319),(.116,.283),(.122,.241),(.115,.215)], TYRE)
            ring([(-.053,.3457),(-.035,.3457),(-.033,.3438),(.032,.3438),(.038,.3457),(.058,.3457)], RUBBER)
            disc(.235,.092,DARK_HUB)
            disc(.202,.105,DARK_ALLOY)
            disc(.172,.109,RIM_SATIN)
            ring([(.103,.166),(.112,.167),(.112,.164),(.103,.163)],CHROME)
            m.ellipsoid((x-.154,WHEEL_Y+.055,side*.849),(.027,.068,.014),PANEL,10,4)
            ring([(.104,.229),(.120,.239),(.128,.235),(.132,.218),(.125,.211),(.113,.214)], POLISH)
            # Five bevelled, diamond-cut pockets replicate the Y split alloys in the side reference.
            outer = [(.055,0),(.100,-.468),(.188,-.571),(.228,-.390),(.232,-.19),(.232,.02),(.231,.24),(.224,.44),(.180,.562),(.102,.451)]
            inner = [(.088,.013),(.116,-.332),(.176,-.416),(.195,-.318),(.205,-.153),(.207,.018),(.203,.177),(.191,.344),(.167,.408),(.115,.325)]
            for i in range(5):
                angle = (i * math.tau/5 + .21)*side
                def point(p, depth):
                    radius, spoke = p
                    return x+radius*math.cos(angle+spoke*side), WHEEL_Y+radius*math.sin(angle+spoke*side), side*(.744+depth)
                for z0,z1,mat in [(.123,.124,RIM_SATIN),(.132,.132,POLISH)]:
                    a=[m.vert(point(p,z0),(0,.05,side),mat) for p in outer]
                    b=[m.vert(point(p,z1),(0,.05,side),mat) for p in inner]
                    for j in range(len(a)):
                        k=(j+1)%len(a)
                        m.tri(a[j],a[k],b[k]); m.tri(a[j],b[k],b[j])
            disc(.071,.135,CHROME)
            disc(.054,.137,TRIM)
            for i in range(5):
                t = math.tau*i/5
                cx,cy=x+.044*math.cos(t),WHEEL_Y+.044*math.sin(t)
                m.poly([(cx+.007*math.cos(u),cy+.007*math.sin(u),side*.884) for u in lin(0,math.tau,6)[:-1]],CHROME,(0,0,side))
            m.ellipsoid((x,WHEEL_Y,side*.886),(.022,.022,.005),DARK_ALLOY,12,4)


def front(m):
    sg = 1
    def p(points, mat, o): decorate(m, sg, points, mat, o)
    p([(-.717,.832),(-.503,.817),(0,.797),(.503,.817),(.717,.832),(.622,.701),(.453,.637),
       (-.453,.637),(-.622,.701)], TRIM, .008)
    p([(-.648,.788),(-.413,.773),(.413,.773),(.648,.788),(.552,.716),(.333,.681),(-.333,.681),(-.552,.716)], FASCIA, .015)
    p([(-.588,.647),(.588,.647),(.526,.494),(.433,.360),(-.433,.360),(-.526,.494)], GLASS_RIM, .011)
    p([(-.560,.619),(.560,.619),(.497,.501),(.416,.381),(-.416,.381),(-.497,.501)], TRIM, .020)
    for i in range(6):
        y=.400+i*.034
        a=.424+(y-.4)*.58
        p([(-a,y), (a,y), (a-.007,y-.009),(-a+.007,y-.009)], PANEL, .024)
    p([(-.51,.358),(.51,.358),(.459,.323),(-.459,.323)], PANEL, .023)
    p([(-.712,.841),(-.461,.824),(.461,.824),(.712,.841),(.625,.812),(-.625,.812)], TRIM, .028)
    p([(-.513,.771),(-.196,.755),(.196,.755),(.513,.771),(.416,.741),(-.416,.741)], FASCIA, .024)
    for side in (-1,1):
        # The distinct low, almond-shaped fog-light pockets wrap the outer bumper.
        def side_poly(coords,mat,off): p([(side*z,y) for z,y in coords],mat,off)
        side_poly([(.487,.393),(.566,.447),(.704,.479),(.784,.470),(.801,.422),(.752,.391),(.548,.377)], PAINT_EDGE,.018)
        side_poly([(.512,.405),(.583,.441),(.724,.463),(.781,.452),(.784,.419),(.739,.402),(.555,.388)], FASCIA,.026)
        side_poly([(.542,.408),(.615,.431),(.720,.448),(.745,.439),(.708,.409),(.556,.397)], TRIM,.031)
        lens_z, lens_y = side*.676, .433
        m.ellipsoid(face(1,lens_z,lens_y,.038),(.016,.033,.046),RIM_SATIN,18,6)
        m.ellipsoid(face(1,lens_z,lens_y,.050),(.014,.023,.028),FOG,18,6)
        m.ribbon([(1.823,.391,side*.813),(1.955,.415,side*.815),(2.093,.447,side*.794)],.021, FASCIA,(.56,.04,side))
        # RU3 lens: swept-back clear reflector chamber and one faceted projector toward the grille.
        lamp_path = [(.475,.787,.014,2.155),(.552,.788,.050,2.147),(.657,.802,.068,2.128),
                     (.746,.815,.067,2.104),(.799,.848,.060,2.012),(.827,.877,.047,1.917),
                     (.844,.901,.032,1.823),(.858,.927,.017,1.704),(.850,.942,.004,1.606)]
        def lampshape(u, v, offset=0):
            low=min(int(u),len(lamp_path)-2)
            z,y,h,x=(mix(a,b,u-low) for a,b in zip(lamp_path[low],lamp_path[low+1]))
            if u < 2.97: x=max(x,face(1,z,y,.019)[0])
            else: z=max(z,abs(flank(min(x,2.121),y+v*h,1)[2])+.010)
            return x+offset*max(.13,1-u/7),y+v*h,side*(z+offset*min(1,u/4))
        def lens(material,a,b,lo,hi,stand):
            m.patch(lambda u,v:lampshape(u,v,stand),lin(a,b,20),[lo,hi],material,normal=(.74,.23,side*.72))
        lens(TRIM,0,8,-1,1,.006)
        lens(FRONT_GLASS,.13,7.91,-.83,.91,.011)
        lens(REFLECTOR,.31,6.82,-.20,.68,.019)
        lens(FRONT_LIGHT,.53,7.36,-.70,-.54,.023)
        lens(CLEAR,.28,7.81,.74,.86,.021)
        for i in (3.45,4.0,4.61,5.23,5.90,6.54,7.16):
            m.ribbon([lampshape(i,-.18,.024),lampshape(i+.10,.66,.024)],.008,FRONT_GLASS,(.64,.24,side*.77))
        inner=[(.61,.08),(1.04,.58),(1.99,.62),(2.42,.29),(2.33,-.36),(1.64,-.65),(.93,-.41)]
        m.poly([lampshape(u,v,.030) for u,v in inner],DARK_ALLOY,(.93,.16,side*.32))
        m.poly([lampshape(u,v,.035) for u,v in [(.87,.09),(1.17,.45),(1.91,.45),(2.14,.19),(2.01,-.34),(1.53,-.48),(1.02,-.31)]],
               PROJECTOR,(.92,.13,side*.27))
        m.ribbon([lampshape(u,v,.040) for u,v in [(1.12,.14),(1.58,.32),(1.91,.27)]],.012,REFLECTOR,(1,.09,side*.17))
        m.ribbon([lampshape(u,-.43,.027) for u in lin(.55,2.65,12)],.009,REFLECTOR,(.81,.22,side*.47))
    decal(m, 1, 0, .744, .169, .145, 'honda', .035)
    decal(m, 1, 0, .525, .596, .121, 'front', .044)


def rear(m):
    def p(points, mat, o): decorate(m, -1, points, mat, o)
    p([(-.397,.927),(.397,.927),(.343,.666),(.214,.61),(-.214,.61),(-.343,.666)], PAINT_EDGE, .007)
    p([(-.327,.916),(.327,.916),(.284,.718),(.219,.672),(-.219,.672),(-.284,.718)], SEAM, .014)
    p([(-.711,.467),(-.564,.408),(.564,.408),(.711,.467),(.675,.341),(-.675,.341)], PANEL, .017)
    m.ribbon([face(-1,z,.481 + .04*(z*z),.021) for z in lin(-.712,.712,14)], .014, PAINT_EDGE, (-1, .14, 0))
    for side in (-1,1):
        def poly(coords, mat, off): p([(z*side,y) for z,y in coords], mat, off)
        poly([(.276,.943),(.372,1.048),(.676,1.100),(.786,1.059),(.787,.816),(.675,.771),(.583,.925)], TRIM, .023)
        poly([(.291,.951),(.379,1.043),(.666,1.088),(.775,1.053),(.781,.954),(.610,.911)], REAR_GLASS, .027)
        poly([(.315,.956),(.675,1.020),(.773,1.026),(.781,.983),(.604,.944)], REAR_LIGHT, .031)
        poly([(.349,1.011),(.378,1.038),(.673,1.078),(.772,1.048),(.774,1.031),(.665,1.057)], REAR_EDGE, .032)
        poly([(.335,.986),(.385,1.031),(.519,1.043),(.545,1.008)], CLEAR, .037)
        poly([(.609,.921),(.778,.952),(.781,.825),(.678,.782)], CLEAR, .036)
        for j in range(4):
            y=.833+j*.022
            poly([(.673-(y-.83)*.55,y-.020),(.775,y+.018),(.774,y+.024),(.673-(y-.83)*.55,y-.015)], CHROME, .04)
        m.poly([(-2.12,1.058,side*.777),(-1.932,1.071,side*.848),(-1.698,1.039,side*.880),
                (-1.791,.967,side*.884),(-2.13,.945,side*.781)], REAR_GLASS, (-.35,.11,side))
        m.ribbon([(-2.158,1.014,side*.776),(-1.943,1.018,side*.857),(-1.753,1.008,side*.884)],
                 .029, REAR_LIGHT, (-.39,.17,side))
        m.ribbon([(-2.159,.982,side*.779),(-1.947,.988,side*.858),(-1.788,.979,side*.885)],
                 .017, REAR_LIGHT, (-.39,.17,side))
        poly([(.539,.466),(.719,.501),(.693,.475),(.552,.446)], REAR_GLASS, .029)
    decal(m, -1, 0, 1.024, .169, .146, 'honda', .032)
    decal(m, -1, 0, .792, .342, .222, 'rear', .036)
    decal(m, -1, -.502, .822, .277, .067, 'vezel', .028)
    decal(m, -1, -.306, .840, .056, .089, 'plaque', .026)
    decal(m, -1, .496, .839, .245, .041, 'hybrid', .033)
    decal(m, -1, .509, .767, .145, .058, 'rs', .027)


def build_model(path):
    mesh = Mesh()
    mesh.poly([(-24, -.015,-24),(24,-.015,-24),(24,-.015,24),(-24,-.015,24)], FLOOR)
    skin(mesh)
    greenhouse(mesh)
    tyres(mesh)
    front(mesh)
    rear(mesh)
    mesh.save(path)
