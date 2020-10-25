package io.github.cottonmc.cottonrpg;

import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer;
import io.github.cottonmc.cottonrpg.data.CharacterData;
import io.github.cottonmc.cottonrpg.data.PlayerCharacterData;
import nerdhub.cardinal.components.api.util.RespawnCopyStrategy;

public final class RpgComponents implements EntityComponentInitializer {
    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.registerForPlayers(CharacterData.KEY, PlayerCharacterData::new, RespawnCopyStrategy.ALWAYS_COPY);
    }
}