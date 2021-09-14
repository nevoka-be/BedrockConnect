package main.com.pyratron.pugmatt.bedrockconnect.listeners;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.nukkitx.math.vector.Vector3f;
import com.nukkitx.network.util.Preconditions;
import com.nukkitx.protocol.bedrock.BedrockPacketCodec;
import com.nukkitx.protocol.bedrock.packet.*;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nukkitx.protocol.bedrock.BedrockServerSession;
import com.nukkitx.protocol.bedrock.handler.BedrockPacketHandler;
import com.nukkitx.protocol.bedrock.packet.LoginPacket;
import com.nukkitx.protocol.bedrock.util.EncryptionUtils;
import main.com.pyratron.pugmatt.bedrockconnect.BCPlayer;
import main.com.pyratron.pugmatt.bedrockconnect.BedrockConnect;
import main.com.pyratron.pugmatt.bedrockconnect.CustomServer;
import main.com.pyratron.pugmatt.bedrockconnect.CustomServerHandler;
import main.com.pyratron.pugmatt.bedrockconnect.Server;
import main.com.pyratron.pugmatt.bedrockconnect.Whitelist;
import main.com.pyratron.pugmatt.bedrockconnect.gui.MainFormButton;
import main.com.pyratron.pugmatt.bedrockconnect.gui.UIComponents;
import main.com.pyratron.pugmatt.bedrockconnect.gui.UIForms;
import main.com.pyratron.pugmatt.bedrockconnect.utils.BedrockProtocol;
import net.minidev.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.security.interfaces.ECPublicKey;
import java.util.ArrayList;
import java.util.List;

public class PacketHandler implements BedrockPacketHandler {
    private static final Logger logger = LogManager.getLogger();

    private Server server;
    private BedrockServerSession session;

    private String name;
    private String uuid;

    private BCPlayer player;

    private JSONObject extraData;

    public void setPlayer(BCPlayer player) {
        this.player = player;
    }

    @Override
    public boolean handle(RequestChunkRadiusPacket packet) {
        ChunkRadiusUpdatedPacket chunkRadiusUpdatePacket = new ChunkRadiusUpdatedPacket();
        chunkRadiusUpdatePacket.setRadius(packet.getRadius());
        session.sendPacketImmediately(chunkRadiusUpdatePacket);

        PlayStatusPacket playStatus = new PlayStatusPacket();
        playStatus.setStatus(PlayStatusPacket.Status.PLAYER_SPAWN);
        session.sendPacket(playStatus);
        return false;
    }

    // Occasionally, a sent form will not correctly send to a player for whatever reason, and they float in space. This works as a way to open the form back up.

    @Override
    public boolean handle(PlayerActionPacket packet) {
        player.movementOpen();
        return false;
    }
    @Override
    public boolean handle(MovePlayerPacket packet) {
        if(packet.getMode() == MovePlayerPacket.Mode.NORMAL || packet.getMode() == MovePlayerPacket.Mode.HEAD_ROTATION)
            player.movementOpen();
        return false;
    }

    @Override
    public boolean handle(ModalFormResponsePacket packet) {
        player.setActive();
        player.resetMovementOpen();

        switch (packet.getFormId()) {
                case UIForms.MAIN:
                    if(UIForms.currentForm == UIForms.MAIN) {
                        // Re-open window if closed
                        if (packet.getFormData().contains("null")) {
                            session.sendPacketImmediately(UIForms.createMain(player.getServerList()));
                            player.setCurrentForm(UIForms.MAIN);
                        } else { // If selecting button
                            int chosen = Integer.parseInt(packet.getFormData().replaceAll("\\s+",""));

                            CustomServer[] customServers = CustomServerHandler.getServers();
                            List<String> playerServers = server.getPlayer(uuid).getServerList();

                            MainFormButton button = UIForms.getMainFormButton(chosen, customServers, playerServers);

                            int serverIndex = UIForms.getServerIndex(chosen, customServers, playerServers);

                            switch(button) {
                                case CONNECT:
                                    session.sendPacketImmediately(UIForms.createDirectConnect());
                                    player.setCurrentForm(UIForms.DIRECT_CONNECT);
                                    break;
                                case REMOVE:
                                    session.sendPacketImmediately(UIForms.createRemoveServer(player.getServerList()));
                                    player.setCurrentForm(UIForms.REMOVE_SERVER);
                                    break;
                                case EXIT:
                                    player.disconnect("Exiting Server List", server);
                                    break;
                                case USER_SERVER:
                                    String address = server.getPlayer(uuid).getServerList().get(serverIndex);

                                    if (address.split(":").length > 1) {
                                        String ip = address.split(":")[0];
                                        String port = address.split(":")[1];

                                        try {
                                            logger.info("Redirect " + name + " to "+ ip + ":" + port);
                                            transfer(ip, Integer.parseInt(port));
                                        } catch (Exception e) {
                                            session.sendPacketImmediately(UIForms.createError("Error connecting to server. Invalid address."));
                                        }
                                    } else {
                                        session.sendPacketImmediately(UIForms.createError("Invalid server address"));
                                    }
                                    break;
                                case CUSTOM_SERVER:
                                    CustomServer server = customServers[serverIndex - playerServers.size()];
                                    logger.info("Redirect " + name + " to "+ server.getName());
                                    transfer(server.getAddress(), server.getPort());
                                    break;
                                case FEATURED_SERVER:
                                    int featuredServer = serverIndex - playerServers.size() - customServers.length;

                                    switch (featuredServer) {
                                        case 0: // Hive
                                            logger.info("Redirect " + name + " to The hive");
                                            transfer("167.114.81.89", 19132);
                                            break;
                                        case 1: // Mineplex
                                            logger.info("Redirect " + name + " to Mineplex");
                                            transfer("108.178.12.125", 19132);
                                            break;
                                        case 2: // Cubecraft
                                            logger.info("Redirect " + name + " to Cubecraft");
                                            transfer("play.cubecraft.net", 19132);
                                            break;
                                        case 3: // Lifeboat
                                            logger.info("Redirect " + name + " to Lifeboat");
                                            transfer("51.222.26.28", 19132);
                                            break;
                                        case 4: // Mineville
                                            logger.info("Redirect " + name + " to Mineville");
                                            transfer("168.62.164.235", 19132);
                                            break;
                                        case 5: // Galaxite
                                            logger.info("Redirect " + name + " to Galaxite");
                                            transfer("51.222.8.223", 19132);
                                            break;
                                    }
                                    break;
                            }
                        }
                    }
                    break;
                case UIForms.DIRECT_CONNECT:
                    try {
                        if(packet.getFormData().contains("null")) {
                            session.sendPacketImmediately(UIForms.createMain(player.getServerList()));
                            player.setCurrentForm(UIForms.MAIN);
                        }
                        else {
                            ArrayList<String> data = UIComponents.getFormData(packet.getFormData());
                            if(data.size() > 1) {
                                // Remove any whitespace
                                data.set(0, data.get(0).replaceAll("\\s",""));
                                data.set(1, data.get(1).replaceAll("\\s",""));

                                if(data.get(0).length() >= 253)
                                    session.sendPacketImmediately(UIForms.createError("Address is too large. (Must be 253 characters or less)"));
                                else if(data.get(1).length() >= 10)
                                    session.sendPacketImmediately(UIForms.createError("Port is too large. (Must be less than 10 characters)"));
                                else if(data.get(2).length() >= 36)
                                    session.sendPacketImmediately(UIForms.createError("Display name is too large. (Must be 36 characters or less)"));
                                else if (!data.get(0).matches("^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$") && !data.get(0).matches("^((?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+[A-Za-z]{2,64}$"))
                                    session.sendPacketImmediately(UIForms.createError("Enter a valid address. (E.g. play.example.net, 172.16.254.1)"));
                                else if (!data.get(1).matches("[0-9]+"))
                                    session.sendPacketImmediately(UIForms.createError("Enter a valid port that contains only numbers"));
                                else if (!data.get(2).isEmpty() && !data.get(2).matches("^[a-zA-Z0-9]+( +[a-zA-Z0-9]+)*$"))
                                    session.sendPacketImmediately(UIForms.createError("Display name can only contain letters, numbers, and spaces between characters"));
                                else {
                                    boolean addServer = Boolean.parseBoolean(data.get(3));
                                    if (addServer) {
                                        List<String> serverList = player.getServerList();
                                        if (serverList.size() >= player.getServerLimit())
                                            session.sendPacketImmediately(UIForms.createError("You have hit your serverlist limit of " + player.getServerLimit() + " servers. Remove some to add more."));
                                        else {
                                            String server;
                                            // If display name is included from form input, add as parameter
                                            if(!data.get(2).isEmpty())
                                                server = data.get(0) + ":" + data.get(1) + ":" + data.get(2);
                                            else
                                                server = data.get(0) + ":" + data.get(1);
                                            serverList.add(server);
                                            player.setServerList(serverList);
                                            transfer(data.get(0).replace(" ", ""), Integer.parseInt(data.get(1)));
                                        }
                                    } else {
                                        TransferPacket tp = new TransferPacket();
                                        tp.setAddress(data.get(0).replace(" ", ""));
                                        tp.setPort(Integer.parseInt(data.get(1)));
                                        session.sendPacketImmediately(tp);
                                    }
                                }
                            }
                        }
                    } catch(Exception e) {
                        session.sendPacketImmediately(UIForms.createError("Please enter a valid IP/Address and port that contains only numbers."));
                    }
                    break;
                case UIForms.REMOVE_SERVER:
                    try {
                        if(packet.getFormData().contains("null")) {
                            session.sendPacketImmediately(UIForms.createMain(player.getServerList()));
                            player.setCurrentForm(UIForms.MAIN);
                        }
                        else {
                            ArrayList<String> data = UIComponents.getFormData(packet.getFormData());

                            int chosen = Integer.parseInt(data.get(0));

                            List<String> serverList = player.getServerList();
                            serverList.remove(chosen);

                            player.setServerList(serverList);

                            session.sendPacketImmediately(UIForms.createMain(serverList));
                        }
                    } catch(Exception e) {
                        session.sendPacketImmediately(UIForms.createError("Invalid server to remove"));
                    }
                    break;
                case UIForms.ERROR:
                    session.sendPacketImmediately(UIForms.createMain(player.getServerList()));
                    break;
                case UIForms.DONATION:
                    session.sendPacketImmediately(UIForms.createMain(player.getServerList()));
                    break;
        }
        return false;
    }

    public void transfer(String ip, int port) {
        TransferPacket tp = new TransferPacket();
        tp.setAddress(ip);
        tp.setPort(port);
        session.sendPacketImmediately(tp);
    }

    @Override
    public boolean handle(SetLocalPlayerAsInitializedPacket packet) {
        session.sendPacketImmediately(UIForms.createMain(player.getServerList()));
        return false;
    }

    public PacketHandler(BedrockServerSession session, Server server, boolean packetListening) {
        this.session = session;
        this.server = server;

        session.addDisconnectHandler((DisconnectReason) -> disconnect());
    }

    public void disconnect() {
        logger.info(name + " disconnected");
        if(player != null)
            server.removePlayer(player);
    }

    private static boolean validateChainData(JsonNode data) throws Exception {
        ECPublicKey lastKey = null;
        boolean validChain = false;
        for (JsonNode node : data) {
            JWSObject jwt = JWSObject.parse(node.asText());

            if (!validChain) {
                validChain = verifyJwt(jwt, EncryptionUtils.getMojangPublicKey());
            }

            if (lastKey != null) {
                verifyJwt(jwt, lastKey);
            }

            JsonNode payloadNode = Server.JSON_MAPPER.readTree(jwt.getPayload().toString());
            JsonNode ipkNode = payloadNode.get("identityPublicKey");
            Preconditions.checkState(ipkNode != null && ipkNode.getNodeType() == JsonNodeType.STRING, "identityPublicKey node is missing in chain");
            lastKey = EncryptionUtils.generateKey(ipkNode.asText());
        }
        return validChain;
    }


    private static boolean verifyJwt(JWSObject jwt, ECPublicKey key) throws JOSEException {
        return jwt.verify(new DefaultJWSVerifierFactory().createJWSVerifier(jwt.getHeader(), key));
    }

    @Override
    public boolean handle(DisconnectPacket packet) {
        return false;
    }

    @Override
    public boolean handle(ResourcePackClientResponsePacket packet) {
        switch (packet.getStatus()) {
            case COMPLETED:
                BedrockConnect.data.userExists(uuid, name, session, this);
                break;
            case HAVE_ALL_PACKS:
                ResourcePackStackPacket rs = new ResourcePackStackPacket();
                //rs.setExperimental(false);
                rs.setForcedToAccept(false);
                rs.setGameVersion("*");
                session.sendPacket(rs);
                break;
            default:
                session.disconnect("disconnectionScreen.resourcePack");
                break;
        }

        return true;
    }

    // Heavily referenced from https://github.com/NukkitX/ProxyPass/blob/master/src/main/java/com/nukkitx/proxypass/network/bedrock/session/UpstreamPacketHandler.java

    @Override
    public boolean handle(LoginPacket packet) {
        BedrockPacketCodec packetCodec = BedrockProtocol.getBedrockCodec(packet.getProtocolVersion());

        if (packetCodec == null) {
            session.setPacketCodec(BedrockProtocol.DEFAULT_BEDROCK_CODEC);

            PlayStatusPacket status = new PlayStatusPacket();

            if (packet.getProtocolVersion() > BedrockProtocol.DEFAULT_BEDROCK_CODEC.getProtocolVersion()) {
                status.setStatus(PlayStatusPacket.Status.LOGIN_FAILED_SERVER_OLD);
            }
            else if (packet.getProtocolVersion() < BedrockProtocol.DEFAULT_BEDROCK_CODEC.getProtocolVersion()) {
                status.setStatus(PlayStatusPacket.Status.LOGIN_FAILED_CLIENT_OLD);
            }

            session.sendPacket(status);
        }

        session.setPacketCodec(packetCodec);

        JsonNode certData;
        try {
            certData = Server.JSON_MAPPER.readTree(packet.getChainData().toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Certificate JSON can not be read.");
        }

        JsonNode certChainData = certData.get("chain");
        if (certChainData.getNodeType() != JsonNodeType.ARRAY) {
            throw new RuntimeException("Certificate data is not valid");
        }

        boolean validChain;
        try {
            validChain = validateChainData(certChainData);

            JWSObject jwt = JWSObject.parse(certChainData.get(certChainData.size() - 1).asText());
            JsonNode payload = Server.JSON_MAPPER.readTree(jwt.getPayload().toBytes());

            if (payload.get("extraData").getNodeType() != JsonNodeType.OBJECT) {
                throw new RuntimeException("AuthData was not found!");
            }

            extraData = (JSONObject) jwt.getPayload().toJSONObject().get("extraData");

            if (payload.get("identityPublicKey").getNodeType() != JsonNodeType.STRING) {
                throw new RuntimeException("Identity Public Key was not found!");
            }
            ECPublicKey identityPublicKey = EncryptionUtils.generateKey(payload.get("identityPublicKey").textValue());

            JWSObject clientJwt = JWSObject.parse(packet.getSkinData().toString());
            verifyJwt(clientJwt, identityPublicKey);

            logger.info("Made it through login - " + "User: " + extraData.getAsString("displayName") + " (" + extraData.getAsString("identity") + ")");


            name = extraData.getAsString("displayName");
            uuid = extraData.getAsString("identity");
            
            
            //whitelist check
            if (Whitelist.hasWhitelist() && !Whitelist.isPlayerWhitelisted(name)) {
            	session.disconnect(Whitelist.getWhitelistMessage());
                logger.info("Kicked " + name + ": \"" + Whitelist.getWhitelistMessage() + "\"");
            }

            PlayStatusPacket status = new PlayStatusPacket();
            status.setStatus(PlayStatusPacket.Status.LOGIN_SUCCESS);
            session.sendPacket(status);

            SetEntityMotionPacket motion = new SetEntityMotionPacket();
            motion.setRuntimeEntityId(1);
            motion.setMotion(Vector3f.ZERO);
            session.sendPacket(motion);

            ResourcePacksInfoPacket resourcePacksInfo = new ResourcePacksInfoPacket();
            resourcePacksInfo.setForcedToAccept(false);
            resourcePacksInfo.setScriptingEnabled(false);
            session.sendPacket(resourcePacksInfo);
        } catch (Exception e) {
            session.disconnect("disconnectionScreen.internalError.cantConnect");
            throw new RuntimeException("Unable to complete login", e);
        }
        return true;
    }


}
