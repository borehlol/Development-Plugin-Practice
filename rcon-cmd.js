const net = require('net');

const HOST = '127.0.0.1';
const PORT = 25575;
const PASSWORD = 'devlocal123';

function sendPacket(socket, id, type, body) {
    const bodyBuf = Buffer.from(body + '\0', 'ascii');
    const size = 4 + 4 + bodyBuf.length + 1;
    const buf = Buffer.alloc(4 + size);
    let offset = 0;
    buf.writeInt32LE(size, offset); offset += 4;
    buf.writeInt32LE(id, offset); offset += 4;
    buf.writeInt32LE(type, offset); offset += 4;
    bodyBuf.copy(buf, offset); offset += bodyBuf.length;
    buf.writeInt8(0, offset);
    socket.write(buf);
}

function readPacket(buf) {
    if (buf.length < 4) return null;
    const size = buf.readInt32LE(0);
    if (buf.length < 4 + size) return null;
    const id = buf.readInt32LE(4);
    const type = buf.readInt32LE(8);
    const body = buf.toString('ascii', 12, 4 + size - 2);
    return { id, type, body, total: 4 + size };
}

async function main() {
    const commands = process.argv.slice(2);
    const socket = net.createConnection(PORT, HOST);
    let buf = Buffer.alloc(0);
    let authed = false;
    let cmdIndex = 0;

    socket.on('connect', () => {
        sendPacket(socket, 1, 3, PASSWORD);
    });

    socket.on('data', (data) => {
        buf = Buffer.concat([buf, data]);
        let pkt;
        while ((pkt = readPacket(buf)) !== null) {
            buf = buf.subarray(pkt.total);
            if (!authed) {
                if (pkt.id === -1) {
                    console.error('RCON auth failed');
                    process.exit(1);
                }
                authed = true;
                console.log('RCON authenticated');
                sendNext();
            } else {
                console.log(`> ${commands[cmdIndex - 1]}\n${pkt.body}`);
                sendNext();
            }
        }
    });

    function sendNext() {
        if (cmdIndex >= commands.length) {
            socket.end();
            process.exit(0);
        }
        sendPacket(socket, 2, 2, commands[cmdIndex]);
        cmdIndex++;
    }

    socket.on('error', (err) => {
        console.error('RCON error:', err.message);
        process.exit(1);
    });
}

main();
