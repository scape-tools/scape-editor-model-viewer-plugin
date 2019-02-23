package plugin

class Matrix4(var values: DoubleArray) {

    fun multiply(other: Matrix4): Matrix4 {
        val result = DoubleArray(16)
        for (row in 0..3) {
            for (col in 0..3) {
                for (i in 0..3) {
                    result[row * 4 + col] += this.values[row * 4 + i] * other.values[i * 4 + col]
                }
            }
        }
        return Matrix4(result)
    }

    fun transform(v: Vertex): Vertex {
        return Vertex(
                v.x * values[0] + v.y * values[4] + v.z * values[8] + v.w * values[12],
                v.x * values[1] + v.y * values[5] + v.z * values[9] + v.w * values[13],
                v.x * values[2] + v.y * values[6] + v.z * values[10] + v.w * values[14],
                v.x * values[3] + v.y * values[7] + v.z * values[11] + v.w * values[15]
        )
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("[")
        for (row in 0..3) {
            for (col in 0..3) {
                sb.append(values[row * 4 + col])
                if (col != 3) {
                    sb.append(",")
                }
            }
            if (row != 3) {
                sb.append(";\n ")
            }
        }
        sb.append("]")
        return sb.toString()
    }
}