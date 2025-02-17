package com.HiWord9.RPRenames.util.config.generation;

import com.HiWord9.RPRenames.RPRenames;
import com.HiWord9.RPRenames.util.config.PropertiesHelper;
import com.HiWord9.RPRenames.util.rename.type.AbstractRename;
import com.HiWord9.RPRenames.util.rename.type.CEMRename;
import com.HiWord9.RPRenames.util.rename.type.CITRename;
import com.HiWord9.RPRenames.util.rename.RenamesManager;
import com.google.gson.Gson;
import net.minecraft.entity.EntityType;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.*;

import static com.HiWord9.RPRenames.util.rename.RenamesManager.*;

public class CEMParser implements Parser {

    private static final String CEM_PATH = "optifine/cem";
    private static final String RANDOM_ENTITY_PATH = "optifine/random/entity/";
    private static final String MOB_PATH = "optifine/mob/";

    private static final String PROP_EXTENSION = ".properties";

    private static final ArrayList<String> checked = new ArrayList<>();

    public void parse(ResourceManager resourceManager, Profiler profiler) {
        profiler.push("rprenames:collecting_cem_renames");

        checked.clear();
        for (Map.Entry<Identifier, Resource> entry : resourceManager.findResources(CEM_PATH,
                s -> {
                    String path = s.getPath();
                    try {
                        String fileName = path.substring(path.lastIndexOf("/") + 1, path.lastIndexOf("."));
                        if (!path.endsWith(".jem")) return false;
                        return (Arrays.stream(CEMList.models).toList().contains(fileName));
                    } catch (Exception e) {
                        return false;
                    }
                }
        ).entrySet()) {
            try {
                for (String jpmFileName : objToParamList(objFromInputStream(entry.getValue().getInputStream()), "model")) {
                    if (jpmFileName == null || !jpmFileName.endsWith(".jpm")) continue;
                    String path = entry.getKey().getPath();
                    parseTextureSourceFile(resourceManager, jpmFileName, path.substring(path.lastIndexOf("/") + 1, path.lastIndexOf(".")), RANDOM_ENTITY_PATH);
                }
            } catch (IOException e) {
                RPRenames.LOGGER.error("Something went wrong while parsing CEM Renames", e);
            }
        }

        for (Map.Entry<Identifier, Resource> entry : resourceManager.findResources(CEM_PATH,
                s -> {
                    String path = s.getPath();
                    try {
                        String fileName = path.substring(path.lastIndexOf("/") + 1, path.lastIndexOf("."));
                        if (!path.endsWith(PROP_EXTENSION)) return false;
                        return (Arrays.stream(CEMList.models).toList().contains(fileName));
                    } catch (Exception e) {
                        return false;
                    }
                }
        ).entrySet()) {
            try {
                Properties p = new Properties();
                p.load(entry.getValue().getInputStream());
                ArrayList<String> numbers = getModelNumsFromProp(p);
                String path = entry.getKey().getPath();
                String pathInCem = path.substring(CEM_PATH.length() + 1, path.lastIndexOf("."));
                for (String n : numbers) {
                    if (n.equals("1")) n = "";
                    String jem = pathInCem + n + ".jem";
                    parseTextureSourceFile(resourceManager, jem, pathInCem.substring(pathInCem.lastIndexOf("/") + 1), MOB_PATH);
                }
            } catch (IOException e) {
                RPRenames.LOGGER.error("Something went wrong while parsing CEM Renames", e);
            }
        }

        for (int c = 0; c < CEMList.textures.length; c++) {
            try {
                parseRawPropertyFile(resourceManager, RANDOM_ENTITY_PATH, c);
                parseRawPropertyFile(resourceManager, MOB_PATH, c);
            } catch (IOException e) {
                RPRenames.LOGGER.error("Something went wrong while parsing CEM Renames", e);
            }
        }
        checked.clear();

        profiler.pop();
    }

    private static void parseRawPropertyFile(ResourceManager resourceManager, String texturePath, int textureNum) throws IOException {
        Identifier identifier = new Identifier(Identifier.DEFAULT_NAMESPACE, texturePath + CEMList.textures[textureNum] + PROP_EXTENSION);
        Optional<Resource> optionalResource = resourceManager.getResource(identifier);
        if (optionalResource.isEmpty()) {
            identifier = new Identifier(Identifier.DEFAULT_NAMESPACE, texturePath + getLastPathPart(CEMList.textures[textureNum]) + PROP_EXTENSION);
            optionalResource = resourceManager.getResource(identifier);
            if (optionalResource.isEmpty()) return;
        }
        Resource resource = optionalResource.get();

        String packName = ParserHelper.validatePackName(resource.getResourcePackName());
        String path = ParserHelper.getFullPathFromIdentifier(packName, identifier);
        if (checked.contains(path)) return;

        propertiesToRenameMob(
                ParserHelper.getPropFromResource(resource),
                packName,
                path,
                CEMList.mobs[textureNum]
        );
    }

    private static void parseTextureSourceFile(ResourceManager resourceManager, String fileWithTextureName, String fileName, String texturePath) throws IOException {
        Optional<Resource> optionalResourceJpm = resourceManager.getResource(new Identifier(Identifier.DEFAULT_NAMESPACE, CEM_PATH + "/" + fileWithTextureName));
        if (optionalResourceJpm.isEmpty()) return;

        Resource resourceJpm = optionalResourceJpm.get();
        ArrayList<String> textures = objToParamList(objFromInputStream(resourceJpm.getInputStream()), "texture");
        if (textures.isEmpty()) return;

        String textureName = prepareTexturePath(textures.get(0));

        Identifier propId = new Identifier(Identifier.DEFAULT_NAMESPACE, texturePath + textureName + PROP_EXTENSION);
        Optional<Resource> optionalResourceProp = resourceManager.getResource(propId);
        if (optionalResourceProp.isEmpty()) return;

        Resource resourceProp = optionalResourceProp.get();

        String packName = ParserHelper.validatePackName(resourceProp.getResourcePackName());
        String path = ParserHelper.getFullPathFromIdentifier(packName, propId);
        checked.add(path);

        int i = Arrays.stream(CEMList.models).toList().indexOf(fileName);
        if (i < 0) return;

        propertiesToRenameMob(
                ParserHelper.getPropFromResource(resourceProp),
                packName,
                path,
                CEMList.mobs[i]
        );
    }

    private static ArrayList<String> getModelNumsFromProp(Properties models) {
        ArrayList<String> numbers = new ArrayList<>();
        try {
            for (String p : models.stringPropertyNames()) {
                if (!p.startsWith("models.")) continue;
                String num = models.getProperty(p);
                if (!numbers.contains(num)) numbers.addAll(List.of(num.split(" ")));
            }
        } catch (Exception e) {
            RPRenames.LOGGER.error("Something went wrong while parsing CEM Renames", e);
        }
        return numbers;
    }

    private static Object objFromInputStream(InputStream inputStream) {
        Object obj = null;
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        try {
            Type type = new com.google.gson.reflect.TypeToken<>() {
            }.getType();
            Gson gson = new Gson();
            obj = gson.fromJson(bufferedReader, type);
            bufferedReader.close();
        } catch (IOException e) {
            RPRenames.LOGGER.error("Something went wrong while parsing CEM Renames", e);
        }
        return obj;
    }

    private static ArrayList<String> objToParamList(Object obj, String param) {
        String string = obj.toString();
        ArrayList<String> list = new ArrayList<>();
        int j = string.length() - param.length();
        for (int i = 0; i < j; i++) {
            if (!string.startsWith(param + "=", i) || (i != 0 && String.valueOf(string.charAt(i - 1)).matches("[a-zA-Z]"))) continue;
            int start = i + param.length() + 1;
            if (!string.contains(",")) continue;
            list.add(string.substring(start, string.indexOf(',', start)));
        }
        return list;
    }

    private static void propertiesToRenameMob(Properties p, String packName, String path, EntityType<?> fileName) {
        ArrayList<String> skins = new ArrayList<>();
        for (String s : p.stringPropertyNames()) {
            if (!s.startsWith("name.")) continue;

            String skin = p.getProperty("skins." + s.substring(5));
            if (skins.contains(skin)) continue;
            skins.add(skin);

            String name = PropertiesHelper.getFirstName(p.getProperty(s), path);
            if (name == null) continue;

            ArrayList<AbstractRename> alreadyExist = RenamesManager.getRenames(CEMRename.DEFAULT_MOB_ITEM);

            AbstractRename rename;
            AbstractRename renameNameOnly = new CEMRename(name);
            CEMRename.Mob mob = new CEMRename.Mob(
                    fileName,
                    CEMList.iconFromType(fileName),
                    p,
                    path.replaceAll("\\\\", "/")
            );

            int i = renameNameOnly.indexIn(alreadyExist, true);
            if (i != -1) {
                AbstractRename renameForItem = alreadyExist.get(i);
                if (renameForItem instanceof CITRename citRename) {
                    if (citRename.same(new CITRename(name, CEMRename.DEFAULT_MOB_ITEM), false)) {
                        alreadyExist.remove(i);
                    }
                }
            }
            rename = new CEMRename(
                    name,
                    packName,
                    path,
                    mob
            );

            if (!rename.isContainedIn(alreadyExist)) {
                ArrayList<AbstractRename> newConfig = new ArrayList<>(alreadyExist);
                newConfig.add(rename);
                renames.put(CEMRename.DEFAULT_MOB_ITEM, newConfig);
            }
        }
    }

    private static String getLastPathPart(String path) {
        return path.substring(path.lastIndexOf("/") + 1);
    }

    private static String prepareTexturePath(String texturePath) {
        if (texturePath.endsWith(".png")) {
            texturePath = texturePath.substring(0, texturePath.length() - 4);
        }
        if (texturePath.startsWith("textures/entity/")) {
            texturePath = texturePath.substring(16);
        }
        return texturePath;
    }
}
